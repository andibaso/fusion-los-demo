package sg.andi.fusionlos.workflow.stage;

import io.temporal.workflow.Async;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;
import sg.andi.fusionlos.activity.BureauActivities;
import sg.andi.fusionlos.activity.ConfigActivities;
import sg.andi.fusionlos.activity.KycActivities;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.activity.NotificationActivities;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.DocumentType;
import sg.andi.fusionlos.domain.Status;
import sg.andi.fusionlos.workflow.ActivityStubs;
import sg.andi.fusionlos.workflow.DocumentUploaded;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.StageStatus;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static sg.andi.fusionlos.domain.Decision.REJECT;
import static sg.andi.fusionlos.domain.Stage.UNDERWRITING;
import static sg.andi.fusionlos.domain.Status.*;
import static sg.andi.fusionlos.workflow.LosSearchAttributes.ASSIGNED_TO;

/**
 * Waits for a human, for days if needed, with a reminder and an escalation timer running alongside.
 * The workflow holds no thread and no resources while it waits, and the timers survive worker restarts.
 */
public class UnderwritingWorkflowImpl implements UnderwritingWorkflow {

    private static final String JUNIOR_UNDERWRITER = "uw.alice";
    private static final String SENIOR_UNDERWRITER = "uw.carol";
    private static final double FOUR_EYES_THRESHOLD = 50_000;
    private static final int MAX_INFO_REQUESTS = 3;

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);
    private final ConfigActivities config = ActivityStubs.internal(ConfigActivities.class);
    private NotificationActivities notifications;
    private KycActivities kyc;
    private BureauActivities bureau;

    // Signals land here; the main loop takes them one at a time.
    private final List<UnderwriterDecision> decisions = new ArrayList<>();
    private final List<DocumentUploaded> uploads = new ArrayList<>();

    private String applicationId;
    private String assignee = JUNIOR_UNDERWRITER;
    private Status status;
    private boolean escalated;
    private String firstApprover;

    @Override
    public StageOutcome run(String applicationId) {
        this.applicationId = applicationId;
        DemoClock clock = config.durations(applicationId);
        notifications = ActivityStubs.external(NotificationActivities.class, clock);
        kyc = ActivityStubs.external(KycActivities.class, clock);
        bureau = ActivityStubs.external(BureauActivities.class, clock);

        boolean fourEyes = loanData.loadSummary(applicationId).amount() > FOUR_EYES_THRESHOLD;
        Instant now = Instant.ofEpochMilli(Workflow.currentTimeMillis());
        loanData.openUnderwritingCase(applicationId, assignee, fourEyes,
                now.plus(clock.reminderAfter()), now.plus(clock.escalateAfter()));
        Workflow.upsertTypedSearchAttributes(ASSIGNED_TO.valueSet(assignee));
        setStatus(UNDERWRITING_PENDING);
        notifications.notifyUnderwriter(applicationId, assignee, "CASE_ASSIGNED");

        // Both SLA timers run in one scope, so a single cancel stops them when the decision is made.
        CancellationScope slaTimers = Workflow.newCancellationScope(() -> {
            Async.procedure(this::remindAfter, clock.reminderAfter());
            Async.procedure(this::escalateAfter, clock.escalateAfter());
        });
        slaTimers.run();

        StageOutcome outcome = awaitDecision(fourEyes);
        slaTimers.cancel();
        return outcome;
    }

    @Override
    public void decision(UnderwriterDecision decision) {
        decisions.add(decision);
    }

    @Override
    public void documentUploaded(DocumentUploaded document) {
        uploads.add(document);
    }

    private StageOutcome awaitDecision(boolean fourEyes) {
        int infoRequests = 0;
        while (true) {
            Workflow.await(() -> !decisions.isEmpty());
            UnderwriterDecision decision = decisions.remove(0);

            switch (decision.decision()) {
                case REJECT -> {
                    return reject(decision);
                }
                case REQUEST_INFO -> {
                    infoRequests++;
                    if (infoRequests > MAX_INFO_REQUESTS) {
                        return reject(new UnderwriterDecision(REJECT, "system", "Too many information requests", null));
                    }
                    requestInfo(decision);
                }
                case APPROVE -> {
                    if (!fourEyes || isSecondApproval(decision)) {
                        return approve(decision);
                    }
                }
            }
        }
    }

    /** 4 eyes: the first approval waits for a second one from a different underwriter. */
    private boolean isSecondApproval(UnderwriterDecision approval) {
        if (firstApprover == null) {
            firstApprover = approval.actor();
            loanData.recordApproval(applicationId, approval);
            setStatus(UNDERWRITING_AWAITING_SECOND_APPROVAL);
            notifications.notifyUnderwriter(applicationId, SENIOR_UNDERWRITER, "SECOND_APPROVAL_NEEDED");
            return false;
        }
        if (firstApprover.equals(approval.actor())) {
            loanData.recordEvent(applicationId, UNDERWRITING, "SAME_USER_APPROVAL_IGNORED", approval.actor(), Map.of());
            return false;
        }
        loanData.recordApproval(applicationId, approval);
        return true;
    }

    private StageOutcome approve(UnderwriterDecision decision) {
        loanData.recordDecision(applicationId, decision);
        return StageOutcome.next();
    }

    private StageOutcome reject(UnderwriterDecision decision) {
        loanData.recordDecision(applicationId, decision);
        setStatus(REJECTED);
        notifications.notifyApplicant(applicationId, "APPLICATION_REJECTED");
        return StageOutcome.done(REJECTED);
    }

    /** Ask the applicant for a document, wait for it, then re-run only the check it affects. */
    private void requestInfo(UnderwriterDecision request) {
        DocumentType item = request.item();
        loanData.recordInfoRequest(applicationId, request);
        setStatus(UNDERWRITING_INFO_REQUESTED);
        notifications.notifyApplicant(applicationId, "INFO_REQUESTED");

        Workflow.await(() -> uploads.stream().anyMatch(upload -> upload.type() == item));
        DocumentUploaded document = uploads.stream().filter(upload -> upload.type() == item).findFirst().orElseThrow();
        uploads.remove(document);
        loanData.markInfoReceived(applicationId, document);

        CheckResult result = switch (item) {
            case PAYSLIP -> bureau.pullCreditReport(applicationId);
            case ID_DOCUMENT -> kyc.verifyIdentity(applicationId);
        };
        loanData.saveCheck(applicationId, item.affectedCheck(), result);
        setStatus(waitingStatus());
    }

    private void remindAfter(Duration delay) {
        Workflow.sleep(delay);
        // The timer has fired: finish the reminder even if a decision arrives meanwhile.
        Workflow.newDetachedCancellationScope(() -> {
            loanData.recordEvent(applicationId, UNDERWRITING, "REMINDER", "system", Map.of("to", assignee));
            notifications.notifyUnderwriter(applicationId, assignee, "REMINDER");
        }).run();
    }

    private void escalateAfter(Duration delay) {
        Workflow.sleep(delay);
        Workflow.newDetachedCancellationScope(() -> {
            escalated = true;
            assignee = SENIOR_UNDERWRITER;
            loanData.escalate(applicationId, SENIOR_UNDERWRITER);
            Workflow.upsertTypedSearchAttributes(ASSIGNED_TO.valueSet(SENIOR_UNDERWRITER));
            if (status == UNDERWRITING_PENDING) {
                setStatus(UNDERWRITING_ESCALATED);
            }
            notifications.notifyUnderwriter(applicationId, SENIOR_UNDERWRITER, "CASE_ESCALATED");
        }).run();
    }

    /** The status to return to once an information request is answered. */
    private Status waitingStatus() {
        if (firstApprover != null) return UNDERWRITING_AWAITING_SECOND_APPROVAL;
        if (escalated) return UNDERWRITING_ESCALATED;
        return UNDERWRITING_PENDING;
    }

    private void setStatus(Status next) {
        status = next;
        StageStatus.set(loanData, applicationId, UNDERWRITING, next);
    }
}

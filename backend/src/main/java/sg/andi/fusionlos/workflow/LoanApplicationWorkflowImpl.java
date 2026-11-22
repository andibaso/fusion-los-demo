package sg.andi.fusionlos.workflow;

import io.temporal.failure.TemporalFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.CompletablePromise;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.workflow.stage.DisbursementWorkflow;
import sg.andi.fusionlos.workflow.stage.IntakeWorkflow;
import sg.andi.fusionlos.workflow.stage.OfferWorkflow;
import sg.andi.fusionlos.workflow.stage.UnderwritingWorkflow;
import sg.andi.fusionlos.workflow.stage.VerificationWorkflow;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static sg.andi.fusionlos.domain.Stage.*;
import static sg.andi.fusionlos.domain.Status.CANCELLED;

/**
 * The parent: runs the five stages in order as child workflows.
 * It holds only the application ID and the current stage. The loan file stays in MongoDB.
 */
public class LoanApplicationWorkflowImpl implements LoanApplicationWorkflow {

    private static final Logger log = Workflow.getLogger(LoanApplicationWorkflowImpl.class);

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);

    private String applicationId;
    private Stage stage;
    private CancellationScope runningStage;
    private boolean cancellable;
    private String cancelReason;

    @Override
    public ApplicationOutcome run(ApplicationInput input) {
        applicationId = input.applicationId();

        StageOutcome outcome = runStage(INTAKE, IntakeWorkflow.class, intake -> intake.run(input));
        if (outcome.proceed()) outcome = runStage(VERIFICATION, VerificationWorkflow.class, v -> v.run(applicationId));
        if (outcome.proceed()) outcome = runStage(UNDERWRITING, UnderwritingWorkflow.class, u -> u.run(applicationId));
        if (outcome.proceed()) outcome = runStage(OFFER, OfferWorkflow.class, o -> o.run(applicationId));
        if (outcome.proceed()) outcome = runStage(DISBURSEMENT, DisbursementWorkflow.class, d -> d.run(applicationId));

        Workflow.upsertTypedSearchAttributes(LosSearchAttributes.STATUS.valueSet(outcome.status().name()));
        return new ApplicationOutcome(applicationId, outcome.status());
    }

    @Override
    public void underwriterDecision(UnderwriterDecision decision) {
        forward(UNDERWRITING, UnderwritingWorkflow.class, underwriting -> underwriting.decision(decision));
    }

    @Override
    public void documentUploaded(DocumentUploaded document) {
        forward(UNDERWRITING, UnderwritingWorkflow.class, underwriting -> underwriting.documentUploaded(document));
    }

    @Override
    public void offerResponse(OfferResponse response) {
        forward(OFFER, OfferWorkflow.class, offer -> offer.response(response));
    }

    @Override
    public void cancel(String reason) {
        if (!cancellable) {
            log.warn("Ignoring cancel for {} in stage {}", applicationId, stage);
            return;
        }
        cancelReason = reason;
        if (runningStage != null) {
            runningStage.cancel(reason); // the child stops its timers and activities, then ends as cancelled
        }
    }

    @Override
    public ApplicationState state() {
        return new ApplicationState(stage, waitingOn());
    }

    /** Starts the stage as a child workflow with ID {applicationId}-{stage} and waits for its outcome. */
    private <W> StageOutcome runStage(Stage next, Class<W> type, Function<W, StageOutcome> call) {
        if (cancelReason != null) {
            return cancelled(next); // cancelled between two stages
        }
        cancellable = next != DISBURSEMENT;
        W child = Workflow.newChildWorkflowStub(type,
                ChildWorkflowOptions.newBuilder().setWorkflowId(next.childWorkflowId(applicationId)).build());

        // The child runs in its own scope, so a cancel signal can stop just this stage.
        CompletablePromise<StageOutcome> outcome = Workflow.newPromise();
        runningStage = Workflow.newCancellationScope(() -> outcome.completeFrom(Async.function(call::apply, child)));
        runningStage.run();

        try {
            // Only switch stage once the child is running, so forwarded signals always have a target.
            Workflow.getWorkflowExecution(child).get();
            stage = next;
            Workflow.upsertTypedSearchAttributes(LosSearchAttributes.STAGE.valueSet(next.name()));
            return outcome.get();
        } catch (TemporalFailure stageStopped) {
            if (cancelReason == null) {
                throw stageStopped;
            }
            return cancelled(next);
        } finally {
            runningStage = null;
        }
    }

    private StageOutcome cancelled(Stage during) {
        loanData.recordEvent(applicationId, during, "CANCELLED", "applicant", Map.of("reason", cancelReason));
        StageStatus.set(loanData, applicationId, during, CANCELLED);
        return StageOutcome.done(CANCELLED);
    }

    /** Sends a signal on to the running stage. Signals for any other stage are ignored. */
    private <W> void forward(Stage target, Class<W> type, Consumer<W> signal) {
        if (stage != target) {
            log.warn("Ignoring signal for {} while application {} is in {}", target, applicationId, stage);
            return;
        }
        try {
            signal.accept(Workflow.newExternalWorkflowStub(type, target.childWorkflowId(applicationId)));
        } catch (RuntimeException e) {
            log.warn("Could not forward signal to {} for {}: {}", target, applicationId, e.getMessage());
        }
    }

    private String waitingOn() {
        if (stage == UNDERWRITING) return "underwriter decision";
        if (stage == OFFER) return "applicant response to offer";
        return null;
    }
}

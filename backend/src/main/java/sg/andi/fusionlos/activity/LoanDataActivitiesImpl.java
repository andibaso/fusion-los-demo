package sg.andi.fusionlos.activity;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.ApplicationRules;
import sg.andi.fusionlos.domain.ApplicationSummary;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.CheckStatus;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.LoanApplication.Applicant;
import sg.andi.fusionlos.domain.LoanApplication.DecisionRecord;
import sg.andi.fusionlos.domain.LoanApplication.Disbursement;
import sg.andi.fusionlos.domain.LoanApplication.InfoRequest;
import sg.andi.fusionlos.domain.LoanApplication.Underwriting;
import sg.andi.fusionlos.domain.LoanApplication.WorkflowRef;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.domain.OfferTerms;
import sg.andi.fusionlos.domain.RateTable;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.domain.Status;
import sg.andi.fusionlos.workflow.ApplicationInput;
import sg.andi.fusionlos.workflow.DocumentUploaded;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static sg.andi.fusionlos.activity.AuditWriter.SYSTEM;

@Component
public class LoanDataActivitiesImpl implements LoanDataActivities {

    private final MongoTemplate mongo;
    private final LoanApplicationRepository applications;
    private final AuditWriter audit;

    public LoanDataActivitiesImpl(MongoTemplate mongo, LoanApplicationRepository applications, AuditWriter audit) {
        this.mongo = mongo;
        this.applications = applications;
        this.audit = audit;
    }

    @Override
    public List<String> validate(ApplicationInput in) {
        return ApplicationRules.validate(in.name(), in.email(), in.monthlyIncome(), in.amount(), in.tenorMonths());
    }

    @Override
    public void createApplication(ApplicationInput in) {
        if (applications.find(in.applicationId()).isPresent()) {
            return; // retried activity, already persisted
        }
        Instant now = Instant.now();
        mongo.insert(new LoanApplication(
                in.applicationId(),
                new Applicant(in.customerId(), in.name(), in.email(), in.monthlyIncome()),
                in.product(), in.amount(), in.tenorMonths(), "SGD",
                null, null,
                Map.of(), null, null,
                new Disbursement(null, null, null, in.applicationId() + "-disb-1"),
                List.of(),
                new WorkflowRef(in.applicationId(), "v1"),
                in.durationsProfile(),
                in.submittedBy(),
                now, now));
        audit.write(in.applicationId(), Stage.INTAKE, "APPLICATION_SUBMITTED", in.customerId(),
                Map.of("amount", in.amount(), "product", in.product()));
    }

    @Override
    public void setStatus(String applicationId, Stage stage, Status status) {
        update(applicationId, new Update().set("stage", stage).set("status", status));
        audit.write(applicationId, stage, "STATUS_CHANGED", SYSTEM, Map.of("status", status.name()));
    }

    @Override
    public ApplicationSummary loadSummary(String applicationId) {
        return new ApplicationSummary(applicationId, applications.get(applicationId).amount());
    }

    @Override
    public void recordWorkflowVersion(String applicationId, String version) {
        update(applicationId, new Update().set("workflow.version", version));
        audit.write(applicationId, Stage.VERIFICATION, "WORKFLOW_VERSION", SYSTEM, Map.of("version", version));
    }

    @Override
    public void recordEvent(String applicationId, Stage stage, String type, String actor, Map<String, String> detail) {
        audit.write(applicationId, stage, type, actor, Map.copyOf(detail));
    }

    @Override
    public void saveChecks(String applicationId, Map<String, CheckResult> checks) {
        Update update = new Update();
        checks.forEach((name, result) -> update.set("checks." + name, result));
        update(applicationId, update);
        Map<String, Object> summary = new TreeMap<>();
        checks.forEach((name, result) -> summary.put(name, result.status().name()));
        audit.write(applicationId, Stage.VERIFICATION, "CHECKS_COMPLETED", SYSTEM, summary);
    }

    @Override
    public void saveCheck(String applicationId, String check, CheckResult result) {
        update(applicationId, new Update().set("checks." + check, result));
        audit.write(applicationId, Stage.UNDERWRITING, "CHECK_RERUN", SYSTEM,
                Map.of("check", check, "status", result.status().name()));
    }

    @Override
    public void openUnderwritingCase(String applicationId, String assignee, boolean fourEyesRequired,
                                     Instant reminderAt, Instant escalateAt) {
        Underwriting underwriting = new Underwriting(assignee, Instant.now(), reminderAt, escalateAt, false,
                null, fourEyesRequired, List.of(), List.of(), reviewReasons(applications.get(applicationId)));
        update(applicationId, new Update().set("underwriting", underwriting));
        audit.write(applicationId, Stage.UNDERWRITING, "ASSIGNED", SYSTEM,
                Map.of("assignedTo", assignee, "fourEyesRequired", fourEyesRequired));
    }

    /** Why the underwriter should look closer. PEP_REVIEW comes from the v2 PEP check. */
    private static List<String> reviewReasons(LoanApplication app) {
        List<String> reasons = new ArrayList<>();
        CheckResult pep = app.checks() == null ? null : app.checks().get("pep");
        if (pep != null && pep.status() == CheckStatus.FLAGGED) {
            reasons.add("PEP_REVIEW");
        }
        return reasons;
    }

    @Override
    public void escalate(String applicationId, String newAssignee) {
        update(applicationId, new Update()
                .set("underwriting.assignedTo", newAssignee)
                .set("underwriting.assignedAt", Instant.now())
                .set("underwriting.escalated", true));
        audit.write(applicationId, Stage.UNDERWRITING, "ESCALATED", SYSTEM, Map.of("assignedTo", newAssignee));
    }

    @Override
    public void recordInfoRequest(String applicationId, UnderwriterDecision request) {
        InfoRequest info = new InfoRequest(request.item().name(), request.actor(), Instant.now(), null);
        update(applicationId, new Update().push("underwriting.infoRequests", info));
        audit.write(applicationId, Stage.UNDERWRITING, "INFO_REQUESTED", request.actor(),
                Map.of("item", request.item().name()));
    }

    @Override
    public void markInfoReceived(String applicationId, DocumentUploaded document) {
        Query openRequest = Query.query(where("_id").is(applicationId)
                .and("underwriting.infoRequests").elemMatch(where("item").is(document.type().name()).and("fulfilledAt").is(null)));
        mongo.updateFirst(openRequest, new Update()
                .set("underwriting.infoRequests.$.fulfilledAt", Instant.now())
                .set("updatedAt", Instant.now()), LoanApplication.class);
        audit.write(applicationId, Stage.UNDERWRITING, "INFO_RECEIVED", document.actor(),
                Map.of("item", document.type().name(), "fileRef", document.fileRef()));
    }

    @Override
    public void recordApproval(String applicationId, UnderwriterDecision approval) {
        DecisionRecord record = new DecisionRecord(approval.decision(), approval.actor(), approval.note(), Instant.now());
        update(applicationId, new Update().push("underwriting.approvals", record));
        audit.write(applicationId, Stage.UNDERWRITING, "APPROVAL", approval.actor(), Map.of());
    }

    @Override
    public void recordDecision(String applicationId, UnderwriterDecision d) {
        DecisionRecord record = new DecisionRecord(d.decision(), d.actor(), d.note(), Instant.now());
        update(applicationId, new Update().set("underwriting.decision", record));
        audit.write(applicationId, Stage.UNDERWRITING, "DECISION", d.actor(),
                Map.of("decision", d.decision().name(), "note", d.note() == null ? "" : d.note()));
    }

    @Override
    public OfferTerms prepareOffer(String applicationId, Instant expiresAt) {
        LoanApplication app = applications.get(applicationId);
        CheckResult bureau = app.checks().get("bureau");
        OfferTerms terms = RateTable.price(app.amount(), app.tenorMonths(), bureau == null ? null : bureau.score());
        update(applicationId, new Update().set("offer",
                new LoanApplication.Offer(terms.rate(), terms.monthlyInstalment(), expiresAt, null, null)));
        audit.write(applicationId, Stage.OFFER, "OFFER_PREPARED", SYSTEM,
                Map.of("rate", terms.rate(), "monthlyInstalment", terms.monthlyInstalment(), "expiresAt", expiresAt.toString()));
        return terms;
    }

    @Override
    public void recordOfferResponse(String applicationId, OfferResponse r) {
        update(applicationId, new Update()
                .set("offer.response", r.choice())
                .set("offer.respondedAt", Instant.now()));
        audit.write(applicationId, Stage.OFFER, "OFFER_RESPONSE", r.actor(), Map.of("response", r.choice().name()));
    }

    @Override
    public String disbursementKey(String applicationId) {
        return applications.get(applicationId).disbursement().idempotencyKey();
    }

    @Override
    public void saveReservation(String applicationId, String reservationRef) {
        saveDisbursementField(applicationId, "reservationRef", reservationRef, "FUNDS_RESERVED");
    }

    @Override
    public void saveLoanAccount(String applicationId, String accountNumber) {
        saveDisbursementField(applicationId, "accountNumber", accountNumber, "LOAN_ACCOUNT_CREATED");
    }

    @Override
    public void saveDisbursementRef(String applicationId, String disbursementRef) {
        saveDisbursementField(applicationId, "disbursementRef", disbursementRef, "FUNDS_DISBURSED");
    }

    private void saveDisbursementField(String applicationId, String field, String value, String eventType) {
        update(applicationId, new Update().set("disbursement." + field, value));
        audit.write(applicationId, Stage.DISBURSEMENT, eventType, SYSTEM, Map.of(field, value));
    }

    private void update(String applicationId, Update update) {
        update.set("updatedAt", Instant.now());
        mongo.updateFirst(Query.query(where("_id").is(applicationId)), update, LoanApplication.class);
    }
}

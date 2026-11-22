package sg.andi.fusionlos.api;

import io.temporal.client.WorkflowClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import sg.andi.fusionlos.api.ApiModels.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import sg.andi.fusionlos.domain.AuditEvent;
import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.DocumentType;
import sg.andi.fusionlos.domain.LoanApplication.DocumentRef;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.domain.Status;
import sg.andi.fusionlos.projection.AuditProjection;
import sg.andi.fusionlos.workflow.ApplicationState;
import sg.andi.fusionlos.workflow.DocumentUploaded;
import sg.andi.fusionlos.workflow.LoanApplicationWorkflow;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

import jakarta.servlet.http.HttpServletRequest;
import sg.andi.fusionlos.auth.CurrentUser;
import sg.andi.fusionlos.auth.DemoUser;
import sg.andi.fusionlos.auth.RequiresRole;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import static sg.andi.fusionlos.auth.Role.*;

/**
 * The UI's entry point. Reads come from MongoDB, writes become workflow starts and signals.
 * This controller never writes status or stage.
 *
 * Applicants see and act on their own applications only. Staff see all of them.
 */
@RestController
@Profile("api")
@RequestMapping("/api/applications")
public class ApplicationController {

    private final WorkflowClient temporal;
    private final LoanApplicationRepository applications;
    private final AuditProjection audit;
    private final ApplicationStarter starter;
    private final HistoryTimeline history;
    private final MongoTemplate mongo;

    public ApplicationController(WorkflowClient temporal, LoanApplicationRepository applications,
                                 AuditProjection audit, ApplicationStarter starter,
                                 HistoryTimeline history, MongoTemplate mongo) {
        this.temporal = temporal;
        this.applications = applications;
        this.audit = audit;
        this.starter = starter;
        this.history = history;
        this.mongo = mongo;
    }

    @PostMapping
    @RequiresRole(APPLICANT)
    public ResponseEntity<CreateApplicationResponse> create(HttpServletRequest http, @RequestBody CreateApplicationRequest req) {
        CreateApplicationResponse result = starter.start(req, null, CurrentUser.of(http).id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @GetMapping
    public List<LoanApplication> list(HttpServletRequest http,
                                      @RequestParam(required = false) Stage stage,
                                      @RequestParam(required = false) Status status,
                                      @RequestParam(required = false) String assignedTo) {
        DemoUser user = CurrentUser.of(http);
        return applications.list(stage, status, assignedTo).stream()
                .filter(app -> canSee(user, app))
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationDetail get(HttpServletRequest http, @PathVariable String id) {
        LoanApplication app = find(http, id);
        return new ApplicationDetail(app, live(app));
    }

    @GetMapping("/{id}/audit")
    public List<AuditEvent> audit(HttpServletRequest http, @PathVariable String id) {
        find(http, id);
        return audit.forApplication(id);
    }

    @PostMapping("/{id}/decision")
    @RequiresRole({UNDERWRITER, SENIOR_UNDERWRITER})
    public ResponseEntity<Void> decision(HttpServletRequest http, @PathVariable String id,
                                         @RequestBody DecisionRequest req) {
        DemoUser underwriter = CurrentUser.of(http);
        String user = underwriter.id();
        LoanApplication app = find(http, id);
        requireStatus(app, Status::isUnderwriting, "not waiting for an underwriting decision");
        if (app.status() == Status.UNDERWRITING_ESCALATED && underwriter.role() != SENIOR_UNDERWRITER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Escalated cases are decided by a senior underwriter");
        }
        DocumentType item = req.decision() == Decision.REQUEST_INFO
                ? (req.item() != null ? req.item() : DocumentType.PAYSLIP)
                : null;
        workflow(id).underwriterDecision(new UnderwriterDecision(req.decision(), user, req.note(), item));
        return ResponseEntity.accepted().build();
    }

    /** Stores the document metadata, then tells the workflow. The workflow decides what to re-check. */
    @PostMapping("/{id}/documents")
    @RequiresRole(APPLICANT)
    public ResponseEntity<Void> uploadDocument(HttpServletRequest http, @PathVariable String id,
                                               @RequestBody DocumentRequest req) {
        String user = CurrentUser.of(http).id();
        LoanApplication app = find(http, id);
        requireStatus(app, status -> !status.isFinal(), "application is closed");
        String fileRef = req.fileRef() != null ? req.fileRef() : "upload-" + req.type().name().toLowerCase() + ".pdf";
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().push("documents", new DocumentRef(req.type().name(), fileRef, Instant.now())),
                LoanApplication.class);
        workflow(id).documentUploaded(new DocumentUploaded(req.type(), fileRef, user));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/history")
    public List<HistoryTimeline.Row> history(HttpServletRequest http, @PathVariable String id) {
        find(http, id);
        return history.forApplication(id);
    }

    @PostMapping("/{id}/offer")
    @RequiresRole(APPLICANT)
    public ResponseEntity<Void> offer(HttpServletRequest http, @PathVariable String id, @RequestBody OfferRequest req) {
        String user = CurrentUser.of(http).id();
        requireStatus(find(http, id), status -> status == Status.OFFER_SENT, "no open offer");
        workflow(id).offerResponse(new OfferResponse(req.response(), user));
        return ResponseEntity.accepted().build();
    }

    /** The applicant withdraws. Not possible once disbursement has started. */
    @PostMapping("/{id}/cancel")
    @RequiresRole(APPLICANT)
    public ResponseEntity<Void> cancel(HttpServletRequest http, @PathVariable String id,
                                       @RequestBody(required = false) CancelRequest req) {
        LoanApplication app = find(http, id);
        requireStatus(app, status -> !status.isFinal() && app.stage() != Stage.DISBURSEMENT,
                "can no longer be cancelled");
        String reason = req != null && req.reason() != null && !req.reason().isBlank() ? req.reason() : "Withdrawn by applicant";
        workflow(id).cancel(reason);
        return ResponseEntity.accepted().build();
    }

    private LiveState live(LoanApplication app) {
        if (app.status() != null && app.status().isFinal()) {
            return new LiveState(true, app.stage(), null); // closed, nothing to ask the workflow
        }
        try {
            ApplicationState state = workflow(app.id()).state();
            return new LiveState(true, state.stage(), state.waitingOn());
        } catch (RuntimeException workerUnavailable) {
            return LiveState.unavailable();
        }
    }

    private LoanApplicationWorkflow workflow(String id) {
        return temporal.newWorkflowStub(LoanApplicationWorkflow.class, id);
    }

    /** Not found, or not yours: an applicant cannot tell the two apart. */
    private LoanApplication find(HttpServletRequest http, String id) {
        return applications.find(id)
                .filter(app -> canSee(CurrentUser.of(http), app))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application " + id + " not found"));
    }

    private static boolean canSee(DemoUser user, LoanApplication app) {
        return user.role() != APPLICANT || user.id().equals(app.submittedBy());
    }

    private static void requireStatus(LoanApplication app, Predicate<Status> allowed, String message) {
        if (app.status() == null || !allowed.test(app.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application " + app.id() + " is " + app.status() + ": " + message);
        }
    }
}

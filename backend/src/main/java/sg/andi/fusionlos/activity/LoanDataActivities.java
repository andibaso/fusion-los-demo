package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;
import sg.andi.fusionlos.domain.ApplicationSummary;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.OfferTerms;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.domain.Status;
import sg.andi.fusionlos.workflow.ApplicationInput;
import sg.andi.fusionlos.workflow.DocumentUploaded;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Every MongoDB read and write the workflows need. This is the only code that writes
 * status and stage on the application document.
 */
@ActivityInterface
public interface LoanDataActivities {

    List<String> validate(ApplicationInput input);

    void createApplication(ApplicationInput input);

    void setStatus(String applicationId, Stage stage, Status status);

    ApplicationSummary loadSummary(String applicationId);

    /** Stamps which workflow version the application runs on, for the UI. */
    void recordWorkflowVersion(String applicationId, String version);

    /** Writes one audit event that is not a status change, for example a reminder. */
    void recordEvent(String applicationId, Stage stage, String type, String actor, Map<String, String> detail);

    void saveChecks(String applicationId, Map<String, CheckResult> checks);

    void saveCheck(String applicationId, String check, CheckResult result);

    void openUnderwritingCase(String applicationId, String assignee, boolean fourEyesRequired,
                              Instant reminderAt, Instant escalateAt);

    void escalate(String applicationId, String newAssignee);

    void recordInfoRequest(String applicationId, UnderwriterDecision request);

    void markInfoReceived(String applicationId, DocumentUploaded document);

    void recordApproval(String applicationId, UnderwriterDecision approval);

    void recordDecision(String applicationId, UnderwriterDecision decision);

    OfferTerms prepareOffer(String applicationId, Instant expiresAt);

    void recordOfferResponse(String applicationId, OfferResponse response);

    String disbursementKey(String applicationId);

    void saveReservation(String applicationId, String reservationRef);

    void saveLoanAccount(String applicationId, String accountNumber);

    void saveDisbursementRef(String applicationId, String disbursementRef);
}

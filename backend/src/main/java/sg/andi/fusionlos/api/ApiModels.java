package sg.andi.fusionlos.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.DocumentType;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.OfferChoice;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.mock.ChaosMode;

/** Request and response bodies for the REST API. */
public final class ApiModels {

    private ApiModels() {}

    public record ApplicantRequest(String name, String email, double monthlyIncome) {}

    /** applicationId is optional. Send it again to make a retried submit idempotent. */
    public record CreateApplicationRequest(
            String applicationId, ApplicantRequest applicant, String product, double amount, int tenorMonths) {}

    public record CreateApplicationResponse(String applicationId, boolean created) {}

    /** item is only read for REQUEST_INFO and defaults to PAYSLIP. */
    public record DecisionRequest(Decision decision, String note, DocumentType item) {}

    public record DocumentRequest(DocumentType type, String fileRef) {}

    public record ChaosRequest(ChaosMode mode, Long latencyMs, Integer failNextN) {}

    public record VerificationSettingRequest(boolean parallel) {}

    public record OfferRequest(OfferChoice response) {}

    public record CancelRequest(String reason) {}

    /** Live view from the workflow query. available is false when the worker cannot answer. */
    public record LiveState(boolean available, Stage stage, String waitingOn) {

        static LiveState unavailable() {
            return new LiveState(false, null, null);
        }
    }

    /** The MongoDB document with the live workflow state merged in under "live". */
    public record ApplicationDetail(@JsonUnwrapped LoanApplication application, LiveState live) {}
}

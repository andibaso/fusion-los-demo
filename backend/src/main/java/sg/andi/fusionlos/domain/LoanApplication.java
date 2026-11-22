package sg.andi.fusionlos.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The application document in MongoDB. This is the system of record for the loan file.
 * Temporal never carries this document; workflows pass the ID and activities load it.
 */
@Document("applications")
public record LoanApplication(
        @Id String id,
        Applicant applicant,
        String product,
        double amount,
        int tenorMonths,
        String currency,
        Status status,
        Stage stage,
        Map<String, CheckResult> checks,
        Underwriting underwriting,
        Offer offer,
        Disbursement disbursement,
        List<DocumentRef> documents,
        WorkflowRef workflow,
        String durationsProfile,
        String submittedBy,
        Instant createdAt,
        Instant updatedAt) {

    public record Applicant(String customerId, String name, String email, double monthlyIncome) {}

    public record Underwriting(
            String assignedTo,
            Instant assignedAt,
            Instant reminderAt,
            Instant escalateAt,
            boolean escalated,
            DecisionRecord decision,
            boolean fourEyesRequired,
            List<DecisionRecord> approvals,
            List<InfoRequest> infoRequests,
            List<String> reviewReasons) {}

    public record InfoRequest(String item, String requestedBy, Instant requestedAt, Instant fulfilledAt) {}

    public record DecisionRecord(Decision decision, String by, String note, Instant at) {}

    public record Offer(
            double rate,
            double monthlyInstalment,
            Instant expiresAt,
            OfferChoice response,
            Instant respondedAt) {}

    public record Disbursement(
            String reservationRef,
            String accountNumber,
            String disbursementRef,
            String idempotencyKey) {}

    public record DocumentRef(String type, String fileRef, Instant uploadedAt) {}

    public record WorkflowRef(String workflowId, String version) {}
}

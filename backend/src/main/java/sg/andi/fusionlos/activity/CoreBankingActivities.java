package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface CoreBankingActivities {

    String reserveFunds(String applicationId);

    String createLoanAccount(String applicationId);

    /** Idempotent: the same key always returns the first disbursement reference. */
    String disburse(String applicationId, String idempotencyKey);

    /** The payout reference for this key, or null if no money moved. */
    String findDisbursement(String applicationId, String idempotencyKey);

    /** Compensation for reserveFunds. */
    void releaseReservation(String applicationId);

    /** Compensation for createLoanAccount. */
    void closeLoanAccount(String applicationId);
}

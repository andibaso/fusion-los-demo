package sg.andi.fusionlos.workflow.stage;

import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import sg.andi.fusionlos.activity.ConfigActivities;
import sg.andi.fusionlos.activity.CoreBankingActivities;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.activity.NotificationActivities;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.workflow.ActivityStubs;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.StageStatus;

import static sg.andi.fusionlos.domain.Stage.DISBURSEMENT;
import static sg.andi.fusionlos.domain.Status.COMPLETED;
import static sg.andi.fusionlos.domain.Status.DISBURSEMENT_COMPENSATED;
import static sg.andi.fusionlos.domain.Status.DISBURSEMENT_RUNNING;

/**
 * Saga: reserve funds, open the loan account, pay out. Each step registers how to undo it.
 * If a later step fails for good, the undo steps run in reverse order.
 */
public class DisbursementWorkflowImpl implements DisbursementWorkflow {

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);
    private final ConfigActivities config = ActivityStubs.internal(ConfigActivities.class);
    // Compensations retry until they succeed: undoing must not give up halfway.
    private final CoreBankingActivities compensations = ActivityStubs.internal(CoreBankingActivities.class);

    @Override
    public StageOutcome run(String applicationId) {
        DemoClock clock = config.durations(applicationId);
        CoreBankingActivities coreBanking = ActivityStubs.coreBanking(CoreBankingActivities.class, clock);
        NotificationActivities notifications = ActivityStubs.external(NotificationActivities.class, clock);

        StageStatus.set(loanData, applicationId, DISBURSEMENT, DISBURSEMENT_RUNNING);
        String idempotencyKey = loanData.disbursementKey(applicationId);

        Saga saga = new Saga(new Saga.Options.Builder().build());
        try {
            loanData.saveReservation(applicationId, coreBanking.reserveFunds(applicationId));
            saga.addCompensation(compensations::releaseReservation, applicationId);

            loanData.saveLoanAccount(applicationId, coreBanking.createLoanAccount(applicationId));
            saga.addCompensation(compensations::closeLoanAccount, applicationId);

            loanData.saveDisbursementRef(applicationId, payOut(coreBanking, applicationId, idempotencyKey));
        } catch (ActivityFailure failed) {
            saga.compensate();
            StageStatus.set(loanData, applicationId, DISBURSEMENT, DISBURSEMENT_COMPENSATED);
            notifications.notifyApplicant(applicationId, "DISBURSEMENT_FAILED");
            return StageOutcome.done(DISBURSEMENT_COMPENSATED);
        }

        notifications.notifyApplicant(applicationId, "LOAN_DISBURSED");
        StageStatus.set(loanData, applicationId, DISBURSEMENT, COMPLETED);
        return StageOutcome.done(COMPLETED);
    }

    /**
     * Before undoing anything, ask core banking whether the money moved anyway.
     * Every attempt can time out after the payment went through; compensating then would be wrong.
     */
    private String payOut(CoreBankingActivities coreBanking, String applicationId, String idempotencyKey) {
        try {
            return coreBanking.disburse(applicationId, idempotencyKey);
        } catch (ActivityFailure failed) {
            String paid = compensations.findDisbursement(applicationId, idempotencyKey);
            if (paid != null) {
                return paid;
            }
            throw failed;
        }
    }
}

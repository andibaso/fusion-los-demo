package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.CoreBankingService;

import java.util.Map;

import static sg.andi.fusionlos.activity.AuditWriter.SYSTEM;
import static sg.andi.fusionlos.domain.Stage.DISBURSEMENT;

@Component
public class CoreBankingActivitiesImpl implements CoreBankingActivities {

    private final LoanApplicationRepository applications;
    private final CoreBankingService coreBanking;
    private final AuditWriter audit;

    public CoreBankingActivitiesImpl(LoanApplicationRepository applications, CoreBankingService coreBanking,
                                     AuditWriter audit) {
        this.applications = applications;
        this.coreBanking = coreBanking;
        this.audit = audit;
    }

    @Override
    public String reserveFunds(String applicationId) {
        LoanApplication app = applications.get(applicationId);
        return coreBanking.reserveFunds(applicationId, app.amount());
    }

    @Override
    public String createLoanAccount(String applicationId) {
        return coreBanking.createLoanAccount(applicationId);
    }

    @Override
    public String disburse(String applicationId, String idempotencyKey) {
        LoanApplication app = applications.get(applicationId);
        return coreBanking.disburse(applicationId, idempotencyKey, app.disbursement().accountNumber(), app.amount());
    }

    @Override
    public String findDisbursement(String applicationId, String idempotencyKey) {
        return coreBanking.findPayout(idempotencyKey);
    }

    @Override
    public void releaseReservation(String applicationId) {
        String reservationRef = applications.get(applicationId).disbursement().reservationRef();
        coreBanking.releaseReservation(applicationId, reservationRef);
        audit.write(applicationId, DISBURSEMENT, "RESERVATION_RELEASED", SYSTEM, Map.of("reservationRef", reservationRef));
    }

    @Override
    public void closeLoanAccount(String applicationId) {
        String accountNumber = applications.get(applicationId).disbursement().accountNumber();
        coreBanking.closeLoanAccount(applicationId, accountNumber);
        audit.write(applicationId, DISBURSEMENT, "LOAN_ACCOUNT_CLOSED", SYSTEM, Map.of("accountNumber", accountNumber));
    }
}

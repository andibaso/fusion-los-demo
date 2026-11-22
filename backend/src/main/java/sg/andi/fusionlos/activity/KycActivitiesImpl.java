package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplication.Applicant;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.KycService;

import java.time.Instant;

@Component
public class KycActivitiesImpl implements KycActivities {

    private final LoanApplicationRepository applications;
    private final KycService kyc;

    public KycActivitiesImpl(LoanApplicationRepository applications, KycService kyc) {
        this.applications = applications;
        this.kyc = kyc;
    }

    @Override
    public CheckResult verifyIdentity(String applicationId) {
        Applicant applicant = applications.get(applicationId).applicant();
        String ref = kyc.verify(applicant.customerId(), applicant.name());
        return CheckResult.passed(ref, Instant.now());
    }
}

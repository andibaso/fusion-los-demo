package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplication.Applicant;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.PepService;
import sg.andi.fusionlos.mock.PepService.Screening;

import java.time.Instant;

@Component
public class PepActivitiesImpl implements PepActivities {

    private final LoanApplicationRepository applications;
    private final PepService pep;

    public PepActivitiesImpl(LoanApplicationRepository applications, PepService pep) {
        this.applications = applications;
        this.pep = pep;
    }

    @Override
    public CheckResult checkPoliticalExposure(String applicationId) {
        Applicant applicant = applications.get(applicationId).applicant();
        Screening screening = pep.screen(applicant.customerId(), applicant.name());
        return screening.match()
                ? CheckResult.flagged(screening.ref(), Instant.now())
                : CheckResult.passed(screening.ref(), Instant.now());
    }
}

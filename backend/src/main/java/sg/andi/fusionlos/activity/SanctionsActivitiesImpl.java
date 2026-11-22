package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplication.Applicant;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.SanctionsService;
import sg.andi.fusionlos.mock.SanctionsService.Screening;

import java.time.Instant;

@Component
public class SanctionsActivitiesImpl implements SanctionsActivities {

    private final LoanApplicationRepository applications;
    private final SanctionsService sanctions;

    public SanctionsActivitiesImpl(LoanApplicationRepository applications, SanctionsService sanctions) {
        this.applications = applications;
        this.sanctions = sanctions;
    }

    @Override
    public CheckResult screen(String applicationId) {
        Applicant applicant = applications.get(applicationId).applicant();
        Screening screening = sanctions.screen(applicant.customerId(), applicant.name());
        return screening.hit()
                ? CheckResult.hit(screening.ref(), Instant.now())
                : CheckResult.passed(screening.ref(), Instant.now());
    }
}

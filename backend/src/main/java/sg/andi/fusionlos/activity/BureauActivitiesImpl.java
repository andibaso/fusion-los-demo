package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.BureauService;
import sg.andi.fusionlos.mock.BureauService.CreditReport;

import java.time.Instant;

@Component
public class BureauActivitiesImpl implements BureauActivities {

    private final LoanApplicationRepository applications;
    private final BureauService bureau;

    public BureauActivitiesImpl(LoanApplicationRepository applications, BureauService bureau) {
        this.applications = applications;
        this.bureau = bureau;
    }

    @Override
    public CheckResult pullCreditReport(String applicationId) {
        var applicant = applications.get(applicationId).applicant();
        CreditReport report = bureau.creditReport(applicant.customerId(), applicant.email());
        return CheckResult.scored(report.ref(), report.score(), Instant.now());
    }
}

package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.ValuationService;

import java.time.Instant;

@Component
public class ValuationActivitiesImpl implements ValuationActivities {

    private final LoanApplicationRepository applications;
    private final ValuationService valuation;

    public ValuationActivitiesImpl(LoanApplicationRepository applications, ValuationService valuation) {
        this.applications = applications;
        this.valuation = valuation;
    }

    @Override
    public CheckResult valueCollateral(String applicationId) {
        LoanApplication app = applications.get(applicationId);
        return CheckResult.passed(valuation.value(applicationId, app.amount()), Instant.now());
    }
}

package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;
import sg.andi.fusionlos.domain.CheckResult;

@ActivityInterface
public interface BureauActivities {
    CheckResult pullCreditReport(String applicationId);
}

package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;
import sg.andi.fusionlos.domain.CheckResult;

/** Added in workflow v2. A match does not reject; it flags the case for the underwriter. */
@ActivityInterface
public interface PepActivities {
    CheckResult checkPoliticalExposure(String applicationId);
}

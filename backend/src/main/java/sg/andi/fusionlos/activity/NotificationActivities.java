package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface NotificationActivities {

    void notifyApplicant(String applicationId, String template);

    void notifyUnderwriter(String applicationId, String underwriter, String template);
}

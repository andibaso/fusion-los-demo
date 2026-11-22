package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.mock.NotificationService;

import java.util.Map;

import static sg.andi.fusionlos.activity.AuditWriter.SYSTEM;

@Component
public class NotificationActivitiesImpl implements NotificationActivities {

    private final LoanApplicationRepository applications;
    private final NotificationService notifications;
    private final AuditWriter audit;

    public NotificationActivitiesImpl(LoanApplicationRepository applications,
                                      NotificationService notifications, AuditWriter audit) {
        this.applications = applications;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Override
    public void notifyApplicant(String applicationId, String template) {
        LoanApplication app = applications.get(applicationId);
        send(app, app.applicant().email(), template);
    }

    @Override
    public void notifyUnderwriter(String applicationId, String underwriter, String template) {
        send(applications.get(applicationId), underwriter, template);
    }

    private void send(LoanApplication app, String recipient, String template) {
        notifications.send(recipient, template, app.id());
        audit.write(app.id(), app.stage(), "NOTIFICATION_SENT", SYSTEM,
                Map.of("to", recipient, "template", template));
    }
}

package sg.andi.fusionlos.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Fake email and push gateway. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ChaosSwitchboard chaos;

    public NotificationService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public void send(String recipient, String template, String applicationId) {
        chaos.apply("notification");
        log.info("Notification {} to {} for {}", template, recipient, applicationId);
    }
}

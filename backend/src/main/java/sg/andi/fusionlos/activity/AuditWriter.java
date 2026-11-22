package sg.andi.fusionlos.activity;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.AuditEvent;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.projection.AuditProjection;

import java.time.Instant;
import java.util.Map;

/** Writes audit events from inside an activity. The event ID comes from the activity, so retries do not duplicate it. */
@Component
class AuditWriter {

    static final String SYSTEM = "system";

    private final AuditProjection audit;

    AuditWriter(AuditProjection audit) {
        this.audit = audit;
    }

    void write(String applicationId, Stage stage, String type, String actor, Map<String, Object> detail) {
        ActivityInfo info = Activity.getExecutionContext().getInfo();
        String eventId = info.getWorkflowId() + "/" + info.getRunId() + "/" + info.getActivityId();
        audit.record(new AuditEvent(eventId, applicationId, Instant.now(), stage, type, actor, detail));
    }
}

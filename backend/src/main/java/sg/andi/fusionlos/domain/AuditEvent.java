package sg.andi.fusionlos.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One transition or notable event, written by an activity.
 * The ID is derived from the activity execution so a retried activity writes it once.
 */
@Document("audit_events")
public record AuditEvent(
        @Id String id,
        String applicationId,
        Instant at,
        Stage stage,
        String type,
        String actor,
        Map<String, Object> detail) {}

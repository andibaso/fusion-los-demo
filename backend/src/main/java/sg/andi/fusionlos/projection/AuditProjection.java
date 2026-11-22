package sg.andi.fusionlos.projection;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.AuditEvent;

import java.util.List;

/**
 * The audit trail as list screens see it. Temporal event history is the authoritative record;
 * this projection exists so the UI never has to query Temporal for lists.
 */
@Component
public class AuditProjection {

    private final MongoTemplate mongo;

    public AuditProjection(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Upsert by ID, so a retried activity does not write the same event twice. */
    public void record(AuditEvent event) {
        mongo.save(event);
    }

    public List<AuditEvent> forApplication(String applicationId) {
        Query query = Query.query(Criteria.where("applicationId").is(applicationId))
                .with(Sort.by(Sort.Direction.ASC, "at"));
        return mongo.find(query, AuditEvent.class);
    }
}

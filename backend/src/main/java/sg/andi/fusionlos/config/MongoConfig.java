package sg.andi.fusionlos.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.index.Index;
import sg.andi.fusionlos.domain.AuditEvent;
import sg.andi.fusionlos.domain.LoanApplication;

@Configuration
public class MongoConfig {

    private final MongoTemplate mongo;

    public MongoConfig(MongoTemplate mongo, MappingMongoConverter converter) {
        this.mongo = mongo;
        // Keep documents clean for the demo: no "_class" field.
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        mongo.indexOps(LoanApplication.class).ensureIndex(new Index().on("status", Sort.Direction.ASC));
        mongo.indexOps(LoanApplication.class).ensureIndex(new Index().on("underwriting.assignedTo", Sort.Direction.ASC));
        mongo.indexOps(AuditEvent.class).ensureIndex(
                new Index().on("applicationId", Sort.Direction.ASC).on("at", Sort.Direction.ASC));
    }
}

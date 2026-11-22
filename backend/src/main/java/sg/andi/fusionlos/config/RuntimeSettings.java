package sg.andi.fusionlos.config;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Component;

/** Switches flipped live from the chaos panel. Shared by API and worker through MongoDB. */
@Component
public class RuntimeSettings {

    @Document("settings")
    record VerificationSetting(@Id String id, boolean parallel) {}

    private static final String VERIFICATION = "verification";

    private final MongoTemplate mongo;

    public RuntimeSettings(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public boolean parallelVerification() {
        VerificationSetting setting = mongo.findById(VERIFICATION, VerificationSetting.class);
        return setting == null || setting.parallel();
    }

    public void setParallelVerification(boolean parallel) {
        mongo.save(new VerificationSetting(VERIFICATION, parallel));
    }
}

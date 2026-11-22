package sg.andi.fusionlos.ops;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.config.FeatureFlags;

import java.lang.management.ManagementFactory;
import java.time.Instant;

/** Lets the Ops screen show a red badge the moment the worker is killed. */
@Component
@Profile("worker")
public class HeartbeatWriter {

    private final MongoTemplate mongo;
    private final FeatureFlags features;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName(); // pid@host

    public HeartbeatWriter(MongoTemplate mongo, FeatureFlags features) {
        this.mongo = mongo;
        this.features = features;
    }

    @Scheduled(fixedRate = 5_000)
    public void beat() {
        mongo.save(new WorkerHeartbeat(workerId, Instant.now(), features.workerBuild()));
    }
}

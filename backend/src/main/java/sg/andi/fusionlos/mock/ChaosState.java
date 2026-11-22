package sg.andi.fusionlos.mock;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Chaos settings for one mock service. Stored in MongoDB so the API process (chaos panel)
 * and the worker process (where the mocks run) share them, and they survive a worker restart.
 */
@Document("chaos")
public record ChaosState(@Id String service, ChaosMode mode, long latencyMs, int failNextN) {}

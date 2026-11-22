package sg.andi.fusionlos.ops;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Written by the worker every 5 seconds. The API calls the worker up while the newest beat is under 15 seconds old. */
@Document("worker_heartbeat")
public record WorkerHeartbeat(@Id String id, Instant at, String build) {}

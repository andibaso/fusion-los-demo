package sg.andi.fusionlos.ops;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.config.DurationProfiles;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.domain.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** The Ops screen, read from MongoDB only. List screens never query Temporal. */
@Component
public class OpsSummary {

    public record WorkerStatus(boolean up, Instant lastSeen, String build) {}

    public record Breach(String id, String applicant, Status status, String assignedTo, Instant dueAt, boolean escalated) {}

    public record Stuck(String id, String applicant, Status status, Instant since) {}

    public record Summary(
            WorkerStatus worker,
            Map<Stage, Long> openByStage,
            Map<Status, Long> byStatus,
            List<Breach> slaBreaches,
            List<Stuck> stuck,
            Duration stuckAfter,
            Map<String, Long> openByVersion) {}

    private static final Duration WORKER_TIMEOUT = Duration.ofSeconds(15);

    /** Statuses where the system is working, not waiting on a person. Sitting here too long means stuck. */
    private static final Set<Status> SYSTEM_WORKING =
            Set.of(Status.INTAKE_RECEIVED, Status.VERIFICATION_RUNNING, Status.DISBURSEMENT_RUNNING);

    private final MongoTemplate mongo;
    private final DurationProfiles durations;

    public OpsSummary(MongoTemplate mongo, DurationProfiles durations) {
        this.mongo = mongo;
        this.durations = durations;
    }

    public WorkerStatus worker() {
        Query newest = new Query().with(Sort.by(Sort.Direction.DESC, "at")).limit(1);
        WorkerHeartbeat beat = mongo.findOne(newest, WorkerHeartbeat.class);
        boolean up = beat != null && beat.at().isAfter(Instant.now().minus(WORKER_TIMEOUT));
        return new WorkerStatus(up, beat == null ? null : beat.at(), beat == null ? null : beat.build());
    }

    public Summary summary() {
        Instant now = Instant.now();
        Duration stuckAfter = durations.active().stuckAfter();
        List<LoanApplication> apps = mongo.findAll(LoanApplication.class);

        Map<Stage, Long> openByStage = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            openByStage.put(stage, apps.stream()
                    .filter(a -> a.stage() == stage && a.status() != null && !a.status().isFinal())
                    .count());
        }

        Map<Status, Long> byStatus = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            long count = apps.stream().filter(a -> a.status() == status).count();
            if (count > 0) byStatus.put(status, count);
        }

        // A breach: an underwriting case still undecided after its reminder deadline.
        List<Breach> breaches = apps.stream()
                .filter(a -> a.status() != null && a.status().isUnderwriting() && a.underwriting() != null)
                .filter(a -> a.underwriting().reminderAt() != null && a.underwriting().reminderAt().isBefore(now))
                .map(a -> new Breach(a.id(), a.applicant().name(), a.status(), a.underwriting().assignedTo(),
                        a.underwriting().reminderAt(), a.underwriting().escalated()))
                .sorted((x, y) -> x.dueAt().compareTo(y.dueAt()))
                .toList();

        List<Stuck> stuck = apps.stream()
                .filter(a -> a.status() != null && SYSTEM_WORKING.contains(a.status()))
                .filter(a -> a.updatedAt().isBefore(now.minus(stuckAfter)))
                .map(a -> new Stuck(a.id(), a.applicant().name(), a.status(), a.updatedAt()))
                .sorted((x, y) -> x.since().compareTo(y.since()))
                .toList();

        // After make deploy-v2, old applications stay on v1 while new ones start on v2.
        Map<String, Long> openByVersion = new TreeMap<>();
        apps.stream()
                .filter(a -> a.status() != null && !a.status().isFinal())
                .forEach(a -> openByVersion.merge(versionOf(a), 1L, Long::sum));

        return new Summary(worker(), openByStage, byStatus, breaches, stuck, stuckAfter, openByVersion);
    }

    private static String versionOf(LoanApplication app) {
        return app.workflow() == null || app.workflow().version() == null ? "v1" : app.workflow().version();
    }
}

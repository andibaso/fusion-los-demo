package sg.andi.fusionlos.api;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Back to an empty demo: terminate every running workflow, then drop the projections.
 * The ID counter is kept on purpose: Temporal still holds the closed workflows, and
 * REJECT_DUPLICATE would refuse an application ID that was used before.
 */
@Component
@Profile("api")
public class DemoReset {

    private static final Logger log = LoggerFactory.getLogger(DemoReset.class);

    private static final List<String> COLLECTIONS = List.of(
            "applications", "audit_events", "worker_heartbeat", "core_banking_ledger", "chaos", "settings");

    private final WorkflowClient temporal;
    private final MongoTemplate mongo;

    public DemoReset(WorkflowClient temporal, MongoTemplate mongo) {
        this.temporal = temporal;
        this.mongo = mongo;
    }

    /** Returns how many workflows were terminated. */
    public int reset() {
        List<WorkflowExecutionMetadata> running =
                temporal.listExecutions("ExecutionStatus=\"Running\"").toList();
        int terminated = 0;
        for (WorkflowExecutionMetadata execution : running) {
            try {
                temporal.newUntypedWorkflowStub(execution.getExecution().getWorkflowId(),
                                Optional.of(execution.getExecution().getRunId()), Optional.empty())
                        .terminate("demo reset");
                terminated++;
            } catch (RuntimeException alreadyClosed) {
                // A stage child closes with its parent; nothing left to do.
            }
        }
        COLLECTIONS.forEach(mongo::dropCollection);
        log.info("Demo reset: terminated {} workflows, dropped {}", terminated, COLLECTIONS);
        return terminated;
    }
}

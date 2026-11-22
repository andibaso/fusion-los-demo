package sg.andi.fusionlos.workflow;

import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sg.andi.fusionlos.workflow.stage.*;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays real histories exported from the demo against the current code.
 * If a code change would break a running application, this fails before it is deployed.
 *
 * histories/ holds one application that ran on v1 and one on v2. Export more with
 * `make export-history ID=APP-2026-000123`.
 */
class ReplayTest {

    private static final File HISTORIES = new File("../histories");

    static Stream<File> histories() {
        File[] files = HISTORIES.listFiles((dir, name) -> name.endsWith(".json"));
        assertTrue(files != null && files.length > 0, "no histories in " + HISTORIES.getAbsolutePath());
        return Arrays.stream(files).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("histories")
    void replaysWithoutNonDeterminism(File history) throws Exception {
        WorkflowReplayer.replayWorkflowExecution(history,
                LoanApplicationWorkflowImpl.class,
                IntakeWorkflowImpl.class,
                VerificationWorkflowImpl.class, // the latest build, with the getVersion guard
                UnderwritingWorkflowImpl.class,
                OfferWorkflowImpl.class,
                DisbursementWorkflowImpl.class);
    }
}

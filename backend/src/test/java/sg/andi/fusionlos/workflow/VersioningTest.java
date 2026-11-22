package sg.andi.fusionlos.workflow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sg.andi.fusionlos.domain.CheckResult;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static sg.andi.fusionlos.domain.Stage.UNDERWRITING;

/** The same workflow file on the v1 and v2 builds. ReplayTest covers runs that span the deploy. */
class VersioningTest extends WorkflowTestBase {

    @Test
    void v1BuildRunsThreeChecks() {
        startWorker(false);
        waitForStage(start(), UNDERWRITING);

        assertEquals(Set.of("kyc", "bureau", "sanctions", "valuation"), savedChecks().keySet());
        verify(pep, never()).checkPoliticalExposure(anyString());
        verify(loanData, never()).recordWorkflowVersion(anyString(), anyString());
    }

    @Test
    void v2BuildAddsThePepCheck() {
        startWorker(true);
        waitForStage(start(), UNDERWRITING);

        assertEquals(Set.of("kyc", "bureau", "sanctions", "pep", "valuation"), savedChecks().keySet());
        verify(pep).checkPoliticalExposure(APP_ID);
        verify(loanData).recordWorkflowVersion(APP_ID, "v2");
    }

    private Map<String, CheckResult> savedChecks() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, CheckResult>> saved = ArgumentCaptor.forClass(Map.class);
        verify(loanData).saveChecks(eq(APP_ID), saved.capture());
        return saved.getValue();
    }
}

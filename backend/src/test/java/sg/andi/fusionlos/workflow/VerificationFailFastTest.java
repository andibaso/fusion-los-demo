package sg.andi.fusionlos.workflow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sg.andi.fusionlos.domain.CheckResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static sg.andi.fusionlos.domain.Stage.VERIFICATION;
import static sg.andi.fusionlos.domain.Status.REJECTED;

class VerificationFailFastTest extends WorkflowTestBase {

    @Test
    void sanctionsHitRejectsWithoutWaitingForTheOtherChecks() {
        when(sanctions.screen(APP_ID)).thenReturn(CheckResult.hit("SAN-1", NOW));
        // Every other check is still running when the hit arrives. (If one finished in the same instant,
        // the in-memory test server rejects the cancel with ACTIVITY_UNKNOWN; the real server does not.)
        when(kyc.verifyIdentity(APP_ID)).thenAnswer(call -> {
            sleep(5_000);
            return CheckResult.passed("KYC-1", NOW);
        });
        when(bureau.pullCreditReport(APP_ID)).thenAnswer(call -> {
            sleep(5_000);
            return CheckResult.scored("CB-1", 712, NOW);
        });
        when(pep.checkPoliticalExposure(APP_ID)).thenAnswer(call -> {
            sleep(5_000);
            return CheckResult.passed("PEP-1", NOW);
        });

        long started = System.currentTimeMillis();
        start();

        assertEquals(REJECTED, result().finalStatus());
        assertTrue(System.currentTimeMillis() - started < 4_000, "should not wait for the other checks");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, CheckResult>> saved = ArgumentCaptor.forClass(Map.class);
        verify(loanData).saveChecks(eq(APP_ID), saved.capture());
        assertEquals(Map.of("sanctions", CheckResult.hit("SAN-1", NOW)), saved.getValue());
        verify(loanData).setStatus(APP_ID, VERIFICATION, REJECTED);
        verify(loanData, never()).openUnderwritingCase(anyString(), anyString(), anyBoolean(), any(), any());
    }
}

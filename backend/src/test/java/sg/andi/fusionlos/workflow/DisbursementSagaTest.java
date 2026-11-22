package sg.andi.fusionlos.workflow;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import sg.andi.fusionlos.mock.InsufficientFundsException;
import sg.andi.fusionlos.mock.MockServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static sg.andi.fusionlos.domain.Stage.DISBURSEMENT;
import static sg.andi.fusionlos.domain.Status.COMPLETED;
import static sg.andi.fusionlos.domain.Status.DISBURSEMENT_COMPENSATED;

class DisbursementSagaTest extends WorkflowTestBase {

    private static final String KEY = APP_ID + "-disb-1";

    @Test
    void failedPayoutUndoesEarlierStepsInReverseOrder() {
        when(coreBanking.disburse(APP_ID, KEY)).thenThrow(new MockServiceException("payout is down"));

        assertEquals(DISBURSEMENT_COMPENSATED, runToDisbursement().finalStatus());

        verify(coreBanking, times(5)).disburse(APP_ID, KEY); // max attempts for money movement
        InOrder undo = inOrder(coreBanking, loanData);
        undo.verify(coreBanking).closeLoanAccount(APP_ID);
        undo.verify(coreBanking).releaseReservation(APP_ID);
        undo.verify(loanData).setStatus(APP_ID, DISBURSEMENT, DISBURSEMENT_COMPENSATED);
        verify(notifications).notifyApplicant(APP_ID, "DISBURSEMENT_FAILED");
    }

    @Test
    void insufficientFundsIsNotRetried() {
        when(coreBanking.disburse(APP_ID, KEY)).thenThrow(new InsufficientFundsException("no funds"));

        assertEquals(DISBURSEMENT_COMPENSATED, runToDisbursement().finalStatus());
        verify(coreBanking, times(1)).disburse(APP_ID, KEY);
    }

    @Test
    void lostPayoutResponseIsFoundByKeyAndNothingIsUndone() {
        when(coreBanking.disburse(APP_ID, KEY)).thenThrow(new MockServiceException("timed out"));
        when(coreBanking.findDisbursement(APP_ID, KEY)).thenReturn("DSB-1");

        assertEquals(COMPLETED, runToDisbursement().finalStatus());
        verify(loanData).saveDisbursementRef(APP_ID, "DSB-1");
        verify(coreBanking, never()).releaseReservation(anyString());
        verify(coreBanking, never()).closeLoanAccount(anyString());
    }
}

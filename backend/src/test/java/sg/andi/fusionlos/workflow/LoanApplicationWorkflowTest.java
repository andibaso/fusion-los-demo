package sg.andi.fusionlos.workflow;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.DocumentType;
import sg.andi.fusionlos.domain.OfferChoice;
import sg.andi.fusionlos.domain.Stage;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static sg.andi.fusionlos.domain.Stage.*;
import static sg.andi.fusionlos.domain.Status.*;

class LoanApplicationWorkflowTest extends WorkflowTestBase {

    @Test
    void happyPathRunsAllFiveStagesToCompleted() {
        LoanApplicationWorkflow workflow = start();

        waitForStage(workflow, UNDERWRITING);
        workflow.underwriterDecision(new UnderwriterDecision(Decision.APPROVE, "uw.alice", "looks good", null));

        waitForStage(workflow, OFFER);
        workflow.offerResponse(new OfferResponse(OfferChoice.ACCEPT, "applicant.ben"));

        assertEquals(COMPLETED, result().finalStatus());

        // Status is written by activities, stage by stage, in order.
        InOrder order = inOrder(loanData);
        order.verify(loanData).createApplication(any());
        order.verify(loanData).setStatus(APP_ID, INTAKE, INTAKE_RECEIVED);
        order.verify(loanData).setStatus(APP_ID, VERIFICATION, VERIFICATION_RUNNING);
        order.verify(loanData).saveChecks(eq(APP_ID), any());
        order.verify(loanData).setStatus(APP_ID, UNDERWRITING, UNDERWRITING_PENDING);
        order.verify(loanData).recordDecision(eq(APP_ID), any());
        order.verify(loanData).setStatus(APP_ID, OFFER, OFFER_SENT);
        order.verify(loanData).recordOfferResponse(eq(APP_ID), any());
        order.verify(loanData).setStatus(APP_ID, DISBURSEMENT, DISBURSEMENT_RUNNING);
        order.verify(loanData).saveDisbursementRef(APP_ID, "DSB-1");
        order.verify(loanData).setStatus(APP_ID, DISBURSEMENT, COMPLETED);

        verify(coreBanking).disburse(APP_ID, APP_ID + "-disb-1");
        verify(notifications).notifyApplicant(APP_ID, "LOAN_DISBURSED");
        verify(notifications, never()).notifyApplicant(anyString(), eq("APPLICATION_REJECTED"));
    }

    @Test
    void underwritingEscalatesToSeniorWhenNobodyDecides() {
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);

        env.sleep(Duration.ofSeconds(50)); // past the 45s demo escalation timer

        verify(loanData).escalate(APP_ID, "uw.carol");
        verify(loanData).setStatus(APP_ID, UNDERWRITING, UNDERWRITING_ESCALATED);
        verify(notifications).notifyUnderwriter(APP_ID, "uw.alice", "REMINDER");
        verify(notifications).notifyUnderwriter(APP_ID, "uw.carol", "CASE_ESCALATED");
    }

    @Test
    void offerExpiresWhenApplicantDoesNotAnswer() {
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);
        workflow.underwriterDecision(approval("uw.alice"));
        waitForStage(workflow, OFFER);

        assertEquals(OFFER_EXPIRED, result().finalStatus());
        verify(notifications).notifyApplicant(APP_ID, "OFFER_EXPIRED");
        verify(coreBanking, never()).reserveFunds(anyString());
    }

    @Test
    void infoRequestWaitsForTheDocumentAndRerunsOnlyTheAffectedCheck() {
        CheckResult newReport = CheckResult.scored("CB-2", 740, NOW);
        when(bureau.pullCreditReport(APP_ID))
                .thenReturn(CheckResult.scored("CB-1", 712, NOW))
                .thenReturn(newReport);
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);

        workflow.underwriterDecision(new UnderwriterDecision(Decision.REQUEST_INFO, "uw.alice", null, DocumentType.PAYSLIP));
        verify(loanData, timeout(10_000)).setStatus(APP_ID, UNDERWRITING, UNDERWRITING_INFO_REQUESTED);
        verify(notifications, timeout(10_000)).notifyApplicant(APP_ID, "INFO_REQUESTED");

        workflow.documentUploaded(new DocumentUploaded(DocumentType.PAYSLIP, "payslip.pdf", "applicant.ben"));
        verify(loanData, timeout(10_000)).saveCheck(APP_ID, "bureau", newReport);
        verify(loanData, timeout(10_000).times(2)).setStatus(APP_ID, UNDERWRITING, UNDERWRITING_PENDING);

        verify(bureau, times(2)).pullCreditReport(APP_ID); // once in verification, once for the payslip
        verify(kyc, times(1)).verifyIdentity(APP_ID);       // KYC is not affected by a payslip

        workflow.underwriterDecision(approval("uw.alice"));
        waitForStage(workflow, OFFER);
    }

    @Test
    void fourEyesIgnoresASecondApprovalFromTheSameUnderwriter() {
        givenALoanOf(80_000);
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);

        workflow.underwriterDecision(approval("uw.alice"));
        verify(loanData, timeout(10_000)).setStatus(APP_ID, UNDERWRITING, UNDERWRITING_AWAITING_SECOND_APPROVAL);

        workflow.underwriterDecision(approval("uw.alice"));
        verify(loanData, timeout(10_000))
                .recordEvent(APP_ID, UNDERWRITING, "SAME_USER_APPROVAL_IGNORED", "uw.alice", Map.of());
        assertEquals(UNDERWRITING, workflow.state().stage());

        workflow.underwriterDecision(approval("uw.carol"));
        waitForStage(workflow, OFFER);
        verify(loanData, times(2)).recordApproval(eq(APP_ID), any());
    }

    @Test
    void cancelDuringUnderwritingStopsTheStageAndItsTimers() {
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);

        workflow.cancel("Found a better rate");

        assertEquals(CANCELLED, result().finalStatus());
        verify(loanData).recordEvent(APP_ID, UNDERWRITING, "CANCELLED", "applicant", Map.of("reason", "Found a better rate"));
        verify(loanData).setStatus(APP_ID, UNDERWRITING, CANCELLED);

        env.sleep(Duration.ofSeconds(60)); // the escalation timer would have fired by now
        verify(loanData, never()).escalate(anyString(), anyString());
        verify(loanData, never()).prepareOffer(anyString(), any());
    }

    @Test
    void cancelIsIgnoredOnceDisbursementHasStarted() {
        when(coreBanking.reserveFunds(APP_ID)).thenAnswer(call -> {
            sleep(1_500); // keep the saga busy while the cancel arrives
            return "RSV-1";
        });
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, UNDERWRITING);
        workflow.underwriterDecision(approval("uw.alice"));
        waitForStage(workflow, OFFER);
        workflow.offerResponse(new OfferResponse(OfferChoice.ACCEPT, "applicant.ben"));
        waitForStage(workflow, DISBURSEMENT);

        workflow.cancel("Too late");

        assertEquals(COMPLETED, result().finalStatus());
        verify(loanData, never()).setStatus(anyString(), any(), eq(CANCELLED));
    }

    @Test
    void stageChildrenUseApplicationIdPlusStage() {
        assertEquals("APP-2026-000001-verification", Stage.VERIFICATION.childWorkflowId(APP_ID));
    }
}

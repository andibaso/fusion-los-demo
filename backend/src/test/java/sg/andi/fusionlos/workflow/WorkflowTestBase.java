package sg.andi.fusionlos.workflow;

import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import sg.andi.fusionlos.activity.*;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.domain.ApplicationSummary;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.OfferChoice;
import sg.andi.fusionlos.domain.OfferTerms;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.workflow.stage.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Runs the real workflow code on Temporal's in-memory test server with mocked activities.
 * Timers skip ahead, so a 45 second escalation takes milliseconds.
 */
abstract class WorkflowTestBase {

    static final String APP_ID = "APP-2026-000001";
    static final String TASK_QUEUE = "test-queue";
    static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");

    TestWorkflowEnvironment env;
    WorkflowClient client;

    final LoanDataActivities loanData = mockActivities(LoanDataActivities.class);
    final ConfigActivities config = mockActivities(ConfigActivities.class);
    final KycActivities kyc = mockActivities(KycActivities.class);
    final BureauActivities bureau = mockActivities(BureauActivities.class);
    final SanctionsActivities sanctions = mockActivities(SanctionsActivities.class);
    final ValuationActivities valuation = mockActivities(ValuationActivities.class);
    final PepActivities pep = mockActivities(PepActivities.class);
    final CoreBankingActivities coreBanking = mockActivities(CoreBankingActivities.class);
    final NotificationActivities notifications = mockActivities(NotificationActivities.class);

    @BeforeEach
    void setUp() {
        givenALoanOf(45_000);
        when(config.durations(APP_ID)).thenReturn(new DemoClock("demo-durations",
                Duration.ofSeconds(20), Duration.ofSeconds(45), Duration.ofSeconds(60),
                Duration.ofSeconds(5), Duration.ofMinutes(10), Duration.ofMinutes(2)));
        when(config.parallelVerification()).thenReturn(true);
        when(loanData.validate(any())).thenReturn(List.of());
        when(kyc.verifyIdentity(APP_ID)).thenReturn(CheckResult.passed("KYC-1", NOW));
        when(bureau.pullCreditReport(APP_ID)).thenReturn(CheckResult.scored("CB-1", 712, NOW));
        when(sanctions.screen(APP_ID)).thenReturn(CheckResult.passed("SAN-1", NOW));
        when(pep.checkPoliticalExposure(APP_ID)).thenReturn(CheckResult.passed("PEP-1", NOW));
        when(loanData.prepareOffer(eq(APP_ID), any())).thenReturn(new OfferTerms(6.2, 1370.5));
        when(loanData.disbursementKey(APP_ID)).thenReturn(APP_ID + "-disb-1");
        when(coreBanking.reserveFunds(APP_ID)).thenReturn("RSV-1");
        when(coreBanking.createLoanAccount(APP_ID)).thenReturn("LA-1");
        when(coreBanking.disburse(APP_ID, APP_ID + "-disb-1")).thenReturn("DSB-1");

        startWorker(true);
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    /** Starts a test server and a worker. pepCheckDeployed=false is the v1 build, true is v2. */
    void startWorker(boolean pepCheckDeployed) {
        if (env != null) {
            env.close();
        }
        env = TestWorkflowEnvironment.newInstance();
        env.registerSearchAttribute("Stage", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute("Status", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute("Amount", IndexedValueType.INDEXED_VALUE_TYPE_DOUBLE);
        env.registerSearchAttribute("AssignedTo", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);

        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                LoanApplicationWorkflowImpl.class, IntakeWorkflowImpl.class,
                UnderwritingWorkflowImpl.class, OfferWorkflowImpl.class, DisbursementWorkflowImpl.class);
        worker.registerWorkflowImplementationFactory(VerificationWorkflow.class,
                () -> new VerificationWorkflowImpl(pepCheckDeployed));
        worker.registerActivitiesImplementations(
                loanData, config, kyc, bureau, sanctions, valuation, pep, coreBanking, notifications);
        env.start();
        client = env.getWorkflowClient();
    }

    void givenALoanOf(double amount) {
        when(loanData.loadSummary(APP_ID)).thenReturn(new ApplicationSummary(APP_ID, amount));
    }

    LoanApplicationWorkflow start() {
        LoanApplicationWorkflow workflow = client.newWorkflowStub(LoanApplicationWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(APP_ID).setTaskQueue(TASK_QUEUE).build());
        WorkflowClient.start(workflow::run, new ApplicationInput(APP_ID, "CUST-1", "Ben Tan", "ben@example.com",
                8500, "PERSONAL_LOAN", 45000, 36, null, "applicant.ben"));
        return workflow;
    }

    ApplicationOutcome result() {
        return client.newUntypedWorkflowStub(APP_ID).getResult(ApplicationOutcome.class);
    }

    /** Approve and accept, then wait for the disbursement stage to finish. */
    ApplicationOutcome runToDisbursement() {
        LoanApplicationWorkflow workflow = start();
        waitForStage(workflow, Stage.UNDERWRITING);
        workflow.underwriterDecision(approval("uw.alice"));
        waitForStage(workflow, Stage.OFFER);
        workflow.offerResponse(new OfferResponse(OfferChoice.ACCEPT, "applicant.ben"));
        return result();
    }

    static UnderwriterDecision approval(String underwriter) {
        return new UnderwriterDecision(Decision.APPROVE, underwriter, null, null);
    }

    /** Polls the state() query. Queries are answered by the test worker in real time. */
    static void waitForStage(LoanApplicationWorkflow workflow, Stage expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!isInStage(workflow, expected)) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for stage " + expected);
            sleep(50);
        }
    }

    private static boolean isInStage(LoanApplicationWorkflow workflow, Stage expected) {
        try {
            return workflow.state().stage() == expected;
        } catch (RuntimeException notStartedYet) {
            return false;
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> T mockActivities(Class<T> type) {
        return mock(type, withSettings().withoutAnnotations());
    }
}

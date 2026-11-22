package sg.andi.fusionlos.worker;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.activity.BureauActivities;
import sg.andi.fusionlos.activity.ConfigActivities;
import sg.andi.fusionlos.activity.CoreBankingActivities;
import sg.andi.fusionlos.activity.KycActivities;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.activity.NotificationActivities;
import sg.andi.fusionlos.activity.PepActivities;
import sg.andi.fusionlos.activity.SanctionsActivities;
import sg.andi.fusionlos.activity.ValuationActivities;
import sg.andi.fusionlos.config.FeatureFlags;
import sg.andi.fusionlos.workflow.LoanApplicationWorkflowImpl;
import sg.andi.fusionlos.workflow.stage.DisbursementWorkflowImpl;
import sg.andi.fusionlos.workflow.stage.IntakeWorkflowImpl;
import sg.andi.fusionlos.workflow.stage.OfferWorkflowImpl;
import sg.andi.fusionlos.workflow.stage.UnderwritingWorkflowImpl;
import sg.andi.fusionlos.workflow.stage.VerificationWorkflow;
import sg.andi.fusionlos.workflow.stage.VerificationWorkflowImpl;

/** Runs only with the "worker" profile. Kill this process and nothing is lost: Temporal keeps the state. */
@Component
@Profile("worker")
public class WorkerMain {

    private static final Logger log = LoggerFactory.getLogger(WorkerMain.class);

    private final WorkerFactory factory;
    private final String build;

    public WorkerMain(WorkflowClient client,
                      LoanDataActivities loanData,
                      ConfigActivities config,
                      KycActivities kyc,
                      BureauActivities bureau,
                      SanctionsActivities sanctions,
                      CoreBankingActivities coreBanking,
                      ValuationActivities valuation,
                      PepActivities pep,
                      NotificationActivities notifications,
                      FeatureFlags features) {
        factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TaskQueues.MAIN);

        worker.registerWorkflowImplementationTypes(
                LoanApplicationWorkflowImpl.class,
                IntakeWorkflowImpl.class,
                UnderwritingWorkflowImpl.class,
                OfferWorkflowImpl.class,
                DisbursementWorkflowImpl.class);
        // The deployed build decides whether new verification runs get the v2 PEP check.
        worker.registerWorkflowImplementationFactory(VerificationWorkflow.class,
                () -> new VerificationWorkflowImpl(features.pepCheck()));
        build = features.workerBuild();

        worker.registerActivitiesImplementations(
                loanData, config, kyc, bureau, sanctions, valuation, pep, coreBanking, notifications);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        factory.start();
        log.info("Worker {} polling task queue {}", build, TaskQueues.MAIN);
    }

    @PreDestroy
    public void stop() {
        factory.shutdown();
    }
}

package sg.andi.fusionlos.workflow.stage;

import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import sg.andi.fusionlos.activity.BureauActivities;
import sg.andi.fusionlos.activity.ConfigActivities;
import sg.andi.fusionlos.activity.KycActivities;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.activity.PepActivities;
import sg.andi.fusionlos.activity.SanctionsActivities;
import sg.andi.fusionlos.activity.ValuationActivities;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.workflow.ActivityStubs;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.StageStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static sg.andi.fusionlos.domain.Stage.VERIFICATION;
import static sg.andi.fusionlos.domain.Status.REJECTED;
import static sg.andi.fusionlos.domain.Status.VERIFICATION_FAILED;
import static sg.andi.fusionlos.domain.Status.VERIFICATION_RUNNING;

/**
 * KYC, credit bureau and sanctions run in parallel; valuation joins for large loans.
 * Each check retries on its own. If the bureau is down, the others finish and only the bureau waits.
 *
 * Workflow v2 adds a PEP check (politically exposed person) to the fan out.
 */
public class VerificationWorkflowImpl implements VerificationWorkflow {

    public static final String PEP_CHANGE = "add-pep-check";
    private static final int WITH_PEP_CHECK = 1;
    private static final double VALUATION_THRESHOLD = 100_000;

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);
    private final ConfigActivities config = ActivityStubs.internal(ConfigActivities.class);

    /** True when the v2 build is deployed. The worker passes it in; see WorkerMain. */
    private final boolean pepCheckDeployed;

    private KycActivities kyc;
    private BureauActivities bureau;
    private SanctionsActivities sanctions;
    private ValuationActivities valuation;
    private PepActivities pep;

    /** The latest build. Used by tests and the replay test. */
    public VerificationWorkflowImpl() {
        this(true);
    }

    public VerificationWorkflowImpl(boolean pepCheckDeployed) {
        this.pepCheckDeployed = pepCheckDeployed;
    }

    @Override
    public StageOutcome run(String applicationId) {
        boolean withPepCheck = choosePath();

        StageStatus.set(loanData, applicationId, VERIFICATION, VERIFICATION_RUNNING);
        DemoClock clock = config.durations(applicationId);
        kyc = ActivityStubs.external(KycActivities.class, clock);
        bureau = ActivityStubs.external(BureauActivities.class, clock);
        sanctions = ActivityStubs.external(SanctionsActivities.class, clock);
        valuation = ActivityStubs.external(ValuationActivities.class, clock);
        pep = ActivityStubs.external(PepActivities.class, clock);
        if (withPepCheck) {
            loanData.recordWorkflowVersion(applicationId, "v2");
        }

        boolean needsValuation = loanData.loadSummary(applicationId).amount() > VALUATION_THRESHOLD;

        Map<String, CheckResult> checks;
        try {
            checks = config.parallelVerification()
                    ? runInParallel(applicationId, needsValuation, withPepCheck)
                    : runInSequence(applicationId, needsValuation, withPepCheck);
        } catch (ActivityFailure gaveUp) {
            if (CancellationScope.current().isCancelRequested()) {
                throw gaveUp; // the application was cancelled, not a failed check
            }
            // A check kept failing until its scheduleToClose deadline. Retries are exhausted.
            StageStatus.set(loanData, applicationId, VERIFICATION, VERIFICATION_FAILED);
            return StageOutcome.done(VERIFICATION_FAILED);
        }
        loanData.saveChecks(applicationId, checks);

        if (checks.get("sanctions").blocksApplication()) {
            StageStatus.set(loanData, applicationId, VERIFICATION, REJECTED);
            return StageOutcome.done(REJECTED);
        }
        return StageOutcome.next();
    }

    /**
     * v1 or v2. getVersion writes a marker into the history the first time it runs, so a run
     * keeps its path for life. Runs started before v2 have no marker and replay as v1.
     */
    private boolean choosePath() {
        if (!pepCheckDeployed) {
            return false; // the v1 build has no getVersion call at all
        }
        int version = Workflow.getVersion(PEP_CHANGE, Workflow.DEFAULT_VERSION, WITH_PEP_CHECK);
        return version == WITH_PEP_CHECK;
    }

    private Map<String, CheckResult> runInParallel(String applicationId, boolean needsValuation, boolean withPepCheck) {
        Map<String, Promise<CheckResult>> running = new LinkedHashMap<>();
        CancellationScope allChecks = Workflow.newCancellationScope(() -> {
            running.put("kyc", Async.function(kyc::verifyIdentity, applicationId));
            running.put("bureau", Async.function(bureau::pullCreditReport, applicationId));
            running.put("sanctions", Async.function(sanctions::screen, applicationId));
            if (withPepCheck) {
                running.put("pep", Async.function(pep::checkPoliticalExposure, applicationId));
            }
            if (needsValuation) {
                running.put("valuation", Async.function(valuation::valueCollateral, applicationId));
            }
        });
        allChecks.run();

        Promise<CheckResult> sanctionsCheck = running.get("sanctions");
        Promise<Void> allDone = Promise.allOf(running.values());

        // Fail fast: stop waiting as soon as sanctions comes back as a hit.
        Workflow.await(() -> allDone.isCompleted() || isHit(sanctionsCheck));
        if (!allDone.isCompleted()) {
            allChecks.cancel("Sanctions hit, other checks are no longer needed");
            return Map.of("sanctions", sanctionsCheck.get());
        }

        allDone.get(); // rethrows if a check gave up
        Map<String, CheckResult> results = new LinkedHashMap<>();
        running.forEach((name, check) -> results.put(name, check.get()));
        results.putIfAbsent("valuation", CheckResult.skipped());
        return results;
    }

    /** The slow path, for the timing comparison on stage. */
    private Map<String, CheckResult> runInSequence(String applicationId, boolean needsValuation, boolean withPepCheck) {
        Map<String, CheckResult> results = new LinkedHashMap<>();
        results.put("kyc", kyc.verifyIdentity(applicationId));
        results.put("bureau", bureau.pullCreditReport(applicationId));
        results.put("sanctions", sanctions.screen(applicationId));
        if (withPepCheck) {
            results.put("pep", pep.checkPoliticalExposure(applicationId));
        }
        results.put("valuation", needsValuation ? valuation.valueCollateral(applicationId) : CheckResult.skipped());
        return results;
    }

    private static boolean isHit(Promise<CheckResult> check) {
        return check.isCompleted() && check.getFailure() == null && check.get().blocksApplication();
    }
}

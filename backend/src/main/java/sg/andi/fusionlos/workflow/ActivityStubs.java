package sg.andi.fusionlos.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.mock.InsufficientFundsException;

import java.time.Duration;

/** Activity stub factory. All timeouts and retry policies for the app live here. */
public final class ActivityStubs {

    private ActivityStubs() {}

    /** Our own MongoDB and config calls: fast, retried with the Temporal defaults. */
    public static <T> T internal(Class<T> activities) {
        return Workflow.newActivityStub(activities, ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .build());
    }

    /**
     * Calls to external services. Retries forever with backoff until scheduleToClose.
     * No retry code anywhere else in the app.
     */
    public static <T> T external(Class<T> activities, DemoClock clock) {
        return Workflow.newActivityStub(activities, ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToCloseTimeout(clock.retryScheduleToClose())
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(clock.retryMaxInterval())
                        .setMaximumAttempts(0) // unlimited
                        .build())
                .build());
    }

    /**
     * Calls that move money. Five attempts, then the saga compensates.
     * Insufficient funds is final, so it is not retried at all.
     */
    public static <T> T coreBanking(Class<T> activities, DemoClock clock) {
        return Workflow.newActivityStub(activities, ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(clock.retryMaxInterval())
                        .setMaximumAttempts(5)
                        .setDoNotRetry(InsufficientFundsException.class.getName())
                        .build())
                .build());
    }
}

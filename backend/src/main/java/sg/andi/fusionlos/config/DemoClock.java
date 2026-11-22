package sg.andi.fusionlos.config;

import java.time.Duration;

/**
 * Every business duration in one place. Two sets exist, "prod-durations" in days and
 * "demo-durations" in seconds; see DurationProfiles. Workflows read this through
 * ConfigActivities, never directly.
 */
public record DemoClock(
        String profile,
        Duration reminderAfter,
        Duration escalateAfter,
        Duration offerExpiresAfter,
        Duration retryMaxInterval,
        Duration retryScheduleToClose,
        Duration stuckAfter) {}

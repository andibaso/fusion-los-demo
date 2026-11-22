package sg.andi.fusionlos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Both duration sets are always loaded so the Ops screen can show them side by side.
 * The Spring profile picks the active one; an application can override it (used by the seeder).
 */
@ConfigurationProperties("los")
public record DurationProfiles(String activeDurations, Map<String, DemoClock> durationProfiles) {

    public DemoClock active() {
        return get(activeDurations);
    }

    /** The named set, or the active one when the name is null. */
    public DemoClock get(String name) {
        String key = name != null ? name : activeDurations;
        DemoClock clock = durationProfiles.get(key);
        if (clock == null) {
            throw new IllegalArgumentException("Unknown durations profile " + key);
        }
        return clock;
    }
}

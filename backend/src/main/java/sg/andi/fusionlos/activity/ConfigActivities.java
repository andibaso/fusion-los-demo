package sg.andi.fusionlos.activity;

import io.temporal.activity.ActivityInterface;
import sg.andi.fusionlos.config.DemoClock;

/** Config goes through an activity so the value is recorded in history and replay stays deterministic. */
@ActivityInterface
public interface ConfigActivities {

    /** The durations for this application: its own override if it has one, else the active set. */
    DemoClock durations(String applicationId);

    /** Chaos panel toggle: run verification checks in parallel (true) or one after another. */
    boolean parallelVerification();
}

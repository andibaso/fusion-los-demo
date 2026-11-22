package sg.andi.fusionlos.mock;

public enum ChaosMode {
    /** Normal behaviour with the service's usual latency. */
    OK,
    /** Succeeds, but takes latencyMs from the chaos panel. */
    SLOW,
    /** Answers quickly with an HTTP 500. */
    ERROR_500,
    /** Hangs longer than the activity timeout. */
    TIMEOUT,
    /** Connection refused. */
    DOWN
}

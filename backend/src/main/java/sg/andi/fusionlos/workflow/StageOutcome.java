package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.Status;

/** Result of one stage child workflow: either move on to the next stage, or end the application with a final status. */
public record StageOutcome(boolean proceed, Status status) {

    public static StageOutcome next() {
        return new StageOutcome(true, null);
    }

    public static StageOutcome done(Status finalStatus) {
        return new StageOutcome(false, finalStatus);
    }
}

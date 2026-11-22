package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.Stage;

/** Answer to the state() query: where the application is and what it is waiting on. */
public record ApplicationState(Stage stage, String waitingOn) {}

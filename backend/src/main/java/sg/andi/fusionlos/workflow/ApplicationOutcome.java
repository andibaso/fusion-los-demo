package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.Status;

public record ApplicationOutcome(String applicationId, Status finalStatus) {}

package sg.andi.fusionlos.domain;

/** The few facts a workflow needs to make routing decisions. Never the whole document. */
public record ApplicationSummary(String applicationId, double amount) {}

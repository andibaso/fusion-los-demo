package sg.andi.fusionlos.domain;

public enum Stage {
    INTAKE, VERIFICATION, UNDERWRITING, OFFER, DISBURSEMENT;

    /** Child workflow ID, for example APP-2026-000123-verification. */
    public String childWorkflowId(String applicationId) {
        return applicationId + "-" + name().toLowerCase();
    }
}

package sg.andi.fusionlos.domain;

public enum Status {
    INTAKE_RECEIVED,
    VERIFICATION_RUNNING, VERIFICATION_FAILED,
    UNDERWRITING_PENDING, UNDERWRITING_INFO_REQUESTED, UNDERWRITING_ESCALATED, UNDERWRITING_AWAITING_SECOND_APPROVAL,
    OFFER_SENT, OFFER_EXPIRED, OFFER_DECLINED,
    DISBURSEMENT_RUNNING, DISBURSEMENT_COMPENSATED,
    COMPLETED, REJECTED, CANCELLED;

    public boolean isFinal() {
        return switch (this) {
            case VERIFICATION_FAILED, OFFER_EXPIRED, OFFER_DECLINED,
                 DISBURSEMENT_COMPENSATED, COMPLETED, REJECTED, CANCELLED -> true;
            default -> false;
        };
    }

    public boolean isUnderwriting() {
        return name().startsWith("UNDERWRITING_");
    }
}

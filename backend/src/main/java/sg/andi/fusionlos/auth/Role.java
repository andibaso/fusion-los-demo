package sg.andi.fusionlos.auth;

public enum Role {
    APPLICANT,
    UNDERWRITER,
    SENIOR_UNDERWRITER,
    OPERATIONS;

    public boolean isUnderwriter() {
        return this == UNDERWRITER || this == SENIOR_UNDERWRITER;
    }
}

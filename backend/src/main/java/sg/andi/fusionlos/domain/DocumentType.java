package sg.andi.fusionlos.domain;

/** Documents an underwriter can ask for, and the one check each one re-runs. */
public enum DocumentType {
    PAYSLIP("bureau"),
    ID_DOCUMENT("kyc");

    private final String affectedCheck;

    DocumentType(String affectedCheck) {
        this.affectedCheck = affectedCheck;
    }

    public String affectedCheck() {
        return affectedCheck;
    }
}

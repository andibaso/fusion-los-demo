package sg.andi.fusionlos.workflow;

/**
 * What the applicant submitted. Only the intake stage sees this; it persists the document
 * and every later stage works from the application ID.
 */
public record ApplicationInput(
        String applicationId,
        String customerId,
        String name,
        String email,
        double monthlyIncome,
        String product,
        double amount,
        int tenorMonths,
        String durationsProfile,  // null: the worker's active durations. Set by the seeder.
        String submittedBy) {}    // the signed-in applicant; null for seeded applications

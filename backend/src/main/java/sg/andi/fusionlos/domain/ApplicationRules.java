package sg.andi.fusionlos.domain;

import java.util.ArrayList;
import java.util.List;

/** Input validation shared by the REST layer (fast feedback) and the intake activity (the rule of record). */
public final class ApplicationRules {

    private ApplicationRules() {}

    public static List<String> validate(String name, String email, double monthlyIncome, double amount, int tenorMonths) {
        List<String> problems = new ArrayList<>();
        if (name == null || name.isBlank()) problems.add("applicant name is required");
        if (email == null || !email.contains("@")) problems.add("a valid email is required");
        if (monthlyIncome <= 0) problems.add("monthly income must be positive");
        if (amount < 1_000 || amount > 1_000_000) problems.add("amount must be between 1,000 and 1,000,000");
        if (tenorMonths < 6 || tenorMonths > 84) problems.add("tenor must be between 6 and 84 months");
        return problems;
    }
}

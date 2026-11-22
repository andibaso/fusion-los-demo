package sg.andi.fusionlos.domain;

/** A deliberately simple pricing table. Real pricing would be a service called from an activity. */
public final class RateTable {

    private RateTable() {}

    public static OfferTerms price(double amount, int tenorMonths, Integer bureauScore) {
        double rate = baseRate(bureauScore) + (tenorMonths > 48 ? 0.5 : 0.0);
        double monthlyRate = rate / 100 / 12;
        double instalment = amount * monthlyRate / (1 - Math.pow(1 + monthlyRate, -tenorMonths));
        return new OfferTerms(rate, Math.round(instalment * 100) / 100.0);
    }

    private static double baseRate(Integer score) {
        if (score == null) return 8.9;
        if (score >= 750) return 5.4;
        if (score >= 700) return 6.2;
        if (score >= 650) return 7.5;
        return 8.9;
    }
}

package sg.andi.fusionlos.mock;

import org.springframework.stereotype.Service;

/**
 * Fake credit bureau. The score is derived from the customer ID so re-runs always match.
 * Seed data only: emails at slow-bureau.example always fail, so those applications sit in
 * verification retrying, which fills the stuck list on the Ops screen.
 */
@Service
public class BureauService {

    public record CreditReport(String ref, int score) {}

    private final ChaosSwitchboard chaos;

    public BureauService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public static final String ALWAYS_FAILING_DOMAIN = "@slow-bureau.example";

    public CreditReport creditReport(String customerId, String email) {
        chaos.apply("bureau");
        if (email.endsWith(ALWAYS_FAILING_DOMAIN)) {
            throw new MockServiceException("bureau returned HTTP 503: report not ready");
        }
        int score = 600 + Math.floorMod(customerId.hashCode(), 200);
        return new CreditReport("CB-" + customerId, score);
    }
}

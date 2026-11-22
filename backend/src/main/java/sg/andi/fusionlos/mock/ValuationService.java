package sg.andi.fusionlos.mock;

import org.springframework.stereotype.Service;

/** Fake collateral valuation, only called for large loans. */
@Service
public class ValuationService {

    private final ChaosSwitchboard chaos;

    public ValuationService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public String value(String applicationId, double amount) {
        chaos.apply("valuation");
        return "VAL-" + applicationId;
    }
}

package sg.andi.fusionlos.mock;

import org.springframework.stereotype.Service;

/** Fake identity verification provider. */
@Service
public class KycService {

    private final ChaosSwitchboard chaos;

    public KycService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public String verify(String customerId, String name) {
        chaos.apply("kyc");
        return "KYC-" + customerId;
    }
}

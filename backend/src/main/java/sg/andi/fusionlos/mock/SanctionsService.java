package sg.andi.fusionlos.mock;

import org.springframework.stereotype.Service;

/** Fake sanctions screening. Any name containing "Blocked" is a hit. */
@Service
public class SanctionsService {

    public record Screening(String ref, boolean hit) {}

    private final ChaosSwitchboard chaos;

    public SanctionsService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public Screening screen(String customerId, String name) {
        chaos.apply("sanctions");
        return new Screening("SAN-" + customerId, name.contains("Blocked"));
    }
}

package sg.andi.fusionlos.mock;

import org.springframework.stereotype.Service;

/** Fake politically exposed person screening. Any name containing "Senator" is a match. */
@Service
public class PepService {

    public record Screening(String ref, boolean match) {}

    private final ChaosSwitchboard chaos;

    public PepService(ChaosSwitchboard chaos) {
        this.chaos = chaos;
    }

    public Screening screen(String customerId, String name) {
        chaos.apply("pep");
        return new Screening("PEP-" + customerId, name.contains("Senator"));
    }
}

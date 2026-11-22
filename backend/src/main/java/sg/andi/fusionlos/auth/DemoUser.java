package sg.andi.fusionlos.auth;

import java.util.List;
import java.util.Optional;

/** The demo cast. IDs match the actors recorded in audit events and workflow signals. */
public record DemoUser(String id, String name, Role role) {

    public static final List<DemoUser> ALL = List.of(
            new DemoUser("applicant.ben", "Ben", Role.APPLICANT),
            new DemoUser("uw.alice", "Alice", Role.UNDERWRITER),
            new DemoUser("uw.carol", "Carol", Role.SENIOR_UNDERWRITER),
            new DemoUser("ops.dave", "Dave", Role.OPERATIONS));

    public static Optional<DemoUser> find(String id) {
        return ALL.stream().filter(u -> u.id().equals(id)).findFirst();
    }
}

package sg.andi.fusionlos.api;

import org.springframework.context.annotation.Profile;
import sg.andi.fusionlos.auth.RequiresRole;
import org.springframework.web.bind.annotation.*;
import sg.andi.fusionlos.config.DurationProfiles;
import sg.andi.fusionlos.mock.CoreBankingService;
import sg.andi.fusionlos.mock.LedgerEntry;
import sg.andi.fusionlos.ops.OpsSummary;

import java.util.List;
import java.util.Map;

import static sg.andi.fusionlos.auth.Role.*;

/** Ops screen, worker badge, durations side by side, seed and reset. */
@RestController
@Profile("api")
@RequestMapping("/api")
public class OpsController {

    public record DurationsView(String active, Map<String, ?> profiles) {}

    private final OpsSummary ops;
    private final DurationProfiles durations;
    private final DemoSeeder seeder;
    private final DemoReset reset;
    private final CoreBankingService coreBanking;

    public OpsController(OpsSummary ops, DurationProfiles durations, DemoSeeder seeder, DemoReset reset,
                         CoreBankingService coreBanking) {
        this.ops = ops;
        this.durations = durations;
        this.seeder = seeder;
        this.reset = reset;
        this.coreBanking = coreBanking;
    }

    @GetMapping("/ops/summary")
    @RequiresRole(OPERATIONS)
    public OpsSummary.Summary summary() {
        return ops.summary();
    }

    /** Open to every signed-in user: the worker badge is in the top bar. */
    @GetMapping("/ops/worker")
    public OpsSummary.WorkerStatus worker() {
        return ops.worker();
    }

    @GetMapping("/config/durations")
    @RequiresRole(OPERATIONS)
    public DurationsView durations() {
        return new DurationsView(durations.activeDurations(), durations.durationProfiles());
    }

    /** What core banking did for one application: one payout, plus any duplicate requests it answered from the key. */
    @GetMapping("/applications/{id}/ledger")
    @RequiresRole({UNDERWRITER, SENIOR_UNDERWRITER, OPERATIONS})
    public List<LedgerEntry> ledger(@PathVariable String id) {
        return coreBanking.ledger(id);
    }

    @PostMapping("/admin/seed")
    @RequiresRole(OPERATIONS)
    public Map<String, Object> seed(@RequestParam(defaultValue = "30") int count) {
        List<String> ids = seeder.seed(count);
        return Map.of("started", ids.size(), "applicationIds", ids);
    }

    @PostMapping("/admin/reset")
    @RequiresRole(OPERATIONS)
    public Map<String, Object> reset() {
        return Map.of("terminated", reset.reset());
    }
}

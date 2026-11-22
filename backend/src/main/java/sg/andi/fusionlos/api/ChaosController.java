package sg.andi.fusionlos.api;

import org.springframework.context.annotation.Profile;
import sg.andi.fusionlos.auth.RequiresRole;
import sg.andi.fusionlos.auth.Role;
import org.springframework.web.bind.annotation.*;
import sg.andi.fusionlos.api.ApiModels.ChaosRequest;
import sg.andi.fusionlos.api.ApiModels.VerificationSettingRequest;
import sg.andi.fusionlos.config.RuntimeSettings;
import sg.andi.fusionlos.mock.ChaosState;
import sg.andi.fusionlos.mock.ChaosSwitchboard;

import java.util.List;
import java.util.Map;

/** Backs the chaos panel. Settings go to MongoDB, so the worker sees them on its next mock call. */
@RestController
@Profile("api")
@RequestMapping("/api")
@RequiresRole(Role.OPERATIONS)
public class ChaosController {

    private final ChaosSwitchboard chaos;
    private final RuntimeSettings settings;

    public ChaosController(ChaosSwitchboard chaos, RuntimeSettings settings) {
        this.chaos = chaos;
        this.settings = settings;
    }

    @GetMapping("/chaos")
    public List<ChaosState> all() {
        return chaos.all();
    }

    @PostMapping("/chaos/{service}")
    public ChaosState set(@PathVariable String service, @RequestBody ChaosRequest req) {
        return chaos.set(service, req.mode(), req.latencyMs(), req.failNextN());
    }

    @PostMapping("/chaos/reset")
    public List<ChaosState> reset() {
        chaos.reset();
        return chaos.all();
    }

    @GetMapping("/config/verification")
    public Map<String, Boolean> verification() {
        return Map.of("parallel", settings.parallelVerification());
    }

    @PostMapping("/config/verification")
    public Map<String, Boolean> setVerification(@RequestBody VerificationSettingRequest req) {
        settings.setParallelVerification(req.parallel());
        return Map.of("parallel", req.parallel());
    }
}

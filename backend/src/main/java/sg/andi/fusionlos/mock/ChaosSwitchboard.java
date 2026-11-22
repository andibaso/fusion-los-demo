package sg.andi.fusionlos.mock;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Every mock call goes through apply(), which honours the chaos settings for that service. */
@Component
public class ChaosSwitchboard {

    /** Normal latency per service in demo mode. */
    public static final Map<String, Long> DEFAULT_LATENCY_MS = defaults();

    private static final long TIMEOUT_HANG_MS = 15_000; // longer than the 10s startToClose timeout

    private final MongoTemplate mongo;

    public ChaosSwitchboard(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void apply(String service) {
        if (consumeFailNext(service)) {
            sleep(200);
            throw new MockServiceException(service + " returned HTTP 500 (fail next N)");
        }
        ChaosState state = get(service);
        switch (state.mode()) {
            case OK -> sleep(DEFAULT_LATENCY_MS.get(service));
            case SLOW -> sleep(state.latencyMs());
            case ERROR_500 -> {
                sleep(200);
                throw new MockServiceException(service + " returned HTTP 500");
            }
            case TIMEOUT -> sleep(TIMEOUT_HANG_MS);
            case DOWN -> throw new MockServiceException(service + " is down: connection refused");
        }
    }

    public ChaosState get(String service) {
        ChaosState stored = mongo.findById(service, ChaosState.class);
        return stored != null ? stored : normal(service);
    }

    public List<ChaosState> all() {
        return DEFAULT_LATENCY_MS.keySet().stream().map(this::get).toList();
    }

    public ChaosState set(String service, ChaosMode mode, Long latencyMs, Integer failNextN) {
        if (!DEFAULT_LATENCY_MS.containsKey(service)) {
            throw new IllegalArgumentException("Unknown service " + service);
        }
        ChaosState state = new ChaosState(service,
                mode != null ? mode : ChaosMode.OK,
                latencyMs != null ? latencyMs : DEFAULT_LATENCY_MS.get(service),
                failNextN != null ? failNextN : 0);
        mongo.save(state);
        return state;
    }

    public void reset() {
        mongo.dropCollection(ChaosState.class);
    }

    private boolean consumeFailNext(String service) {
        ChaosState updated = mongo.findAndModify(
                Query.query(where("_id").is(service).and("failNextN").gt(0)),
                new Update().inc("failNextN", -1),
                FindAndModifyOptions.options().returnNew(true),
                ChaosState.class);
        return updated != null;
    }

    private static ChaosState normal(String service) {
        return new ChaosState(service, ChaosMode.OK, DEFAULT_LATENCY_MS.get(service), 0);
    }

    private static Map<String, Long> defaults() {
        Map<String, Long> latency = new LinkedHashMap<>();
        latency.put("kyc", 2_000L);
        latency.put("bureau", 8_000L);
        latency.put("sanctions", 4_000L);
        latency.put("valuation", 6_000L);
        latency.put("pep", 3_000L);         // workflow v2 only
        latency.put("coreBanking", 1_000L); // reserve funds, open loan account
        latency.put("payout", 1_000L);      // the core banking payout call, on its own switch
        latency.put("notification", 200L);
        return Collections.unmodifiableMap(latency); // keeps insertion order for the chaos panel
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MockServiceException("interrupted");
        }
    }
}

package sg.andi.fusionlos.mock;

import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Fake core banking system. A payout is idempotent on its key, like a real payments API should be:
 * the same key twice pays once and returns the first reference.
 */
@Service
public class CoreBankingService {

    private static final Logger log = LoggerFactory.getLogger(CoreBankingService.class);
    private static final String SERVICE = "coreBanking";
    private static final String PAYOUT = "payout"; // own chaos switch, so a payout can fail on its own
    private static final double FUNDS_AVAILABLE_PER_LOAN = 400_000;

    private final ChaosSwitchboard chaos;
    private final MongoTemplate mongo;

    public CoreBankingService(ChaosSwitchboard chaos, MongoTemplate mongo) {
        this.chaos = chaos;
        this.mongo = mongo;
    }

    public String reserveFunds(String applicationId, double amount) {
        chaos.apply(SERVICE);
        String ref = "RSV-" + number(applicationId);
        record(ref, applicationId, LedgerEntry.RESERVE, ref, amount);
        return ref;
    }

    public String createLoanAccount(String applicationId) {
        chaos.apply(SERVICE);
        String account = "LA-" + number(applicationId);
        record(account, applicationId, LedgerEntry.OPEN_ACCOUNT, account, null);
        return account;
    }

    /**
     * Pays out once per idempotency key. In TIMEOUT mode the money moves and then the response is lost,
     * which is the case idempotency exists for: the retry must not pay a second time.
     */
    public String disburse(String applicationId, String idempotencyKey, String accountNumber, double amount) {
        if (amount > FUNDS_AVAILABLE_PER_LOAN) {
            throw new InsufficientFundsException("Insufficient funds to pay out " + amount);
        }
        boolean responseLost = chaos.get(PAYOUT).mode() == ChaosMode.TIMEOUT;
        if (!responseLost) {
            chaos.apply(PAYOUT); // fails before any money moves
        }

        String ref = "DSB-" + number(applicationId);
        UpdateResult result = mongo.upsert(Query.query(where("_id").is(idempotencyKey)),
                new Update()
                        .setOnInsert("applicationId", applicationId)
                        .setOnInsert("type", LedgerEntry.PAYOUT)
                        .setOnInsert("ref", ref)
                        .setOnInsert("amount", amount)
                        .setOnInsert("at", Instant.now()),
                LedgerEntry.class);
        if (result.getUpsertedId() != null) {
            log.info("Paid out {} to {} (key {}, ref {})", amount, accountNumber, idempotencyKey, ref);
        } else {
            log.info("Duplicate payout request for key {}, returning {} without paying again", idempotencyKey, ref);
            record(UUID.randomUUID().toString(), applicationId, LedgerEntry.DUPLICATE_PAYOUT_REQUEST, ref, amount);
        }

        if (responseLost) {
            chaos.apply(PAYOUT); // hangs past the activity timeout
        }
        return ref;
    }

    /** Looks a payout up by its key. Always answers in the demo, like the compensations. */
    public String findPayout(String idempotencyKey) {
        LedgerEntry entry = mongo.findById(idempotencyKey, LedgerEntry.class);
        return entry == null ? null : entry.ref();
    }

    /** Compensation. Never fails in the demo. */
    public void releaseReservation(String applicationId, String reservationRef) {
        record("REL-" + number(applicationId), applicationId, LedgerEntry.RELEASE, reservationRef, null);
        log.info("Released reservation {}", reservationRef);
    }

    /** Compensation. Never fails in the demo. */
    public void closeLoanAccount(String applicationId, String accountNumber) {
        record("CLS-" + number(applicationId), applicationId, LedgerEntry.CLOSE_ACCOUNT, accountNumber, null);
        log.info("Closed loan account {}", accountNumber);
    }

    public List<LedgerEntry> ledger(String applicationId) {
        return mongo.find(Query.query(where("applicationId").is(applicationId)).with(Sort.by("at")), LedgerEntry.class);
    }

    /** Same ID twice overwrites, so a retried call leaves one row. */
    private void record(String id, String applicationId, String type, String ref, Double amount) {
        mongo.save(new LedgerEntry(id, applicationId, type, ref, amount, Instant.now()));
    }

    private static String number(String applicationId) {
        return applicationId.replace("APP-", "");
    }
}

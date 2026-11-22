package sg.andi.fusionlos.mock;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * What the fake core banking system did, one row per call that changed something.
 * Kept in MongoDB so the API can show it and so a payout stays idempotent across worker restarts.
 */
@Document("core_banking_ledger")
public record LedgerEntry(@Id String id, String applicationId, String type, String ref, Double amount, Instant at) {

    public static final String RESERVE = "RESERVE";
    public static final String OPEN_ACCOUNT = "OPEN_ACCOUNT";
    public static final String PAYOUT = "PAYOUT";
    public static final String DUPLICATE_PAYOUT_REQUEST = "DUPLICATE_PAYOUT_REQUEST";
    public static final String RELEASE = "RELEASE";
    public static final String CLOSE_ACCOUNT = "CLOSE_ACCOUNT";
}

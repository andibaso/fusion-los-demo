package sg.andi.fusionlos.mock;

/** Core banking refuses the payout. Retrying will not help, so the retry policy marks it non retryable. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

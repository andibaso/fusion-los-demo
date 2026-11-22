package sg.andi.fusionlos.mock;

/** What a failing downstream call looks like to the activity. Temporal retries it. */
public class MockServiceException extends RuntimeException {
    public MockServiceException(String message) {
        super(message);
    }
}

package sg.andi.fusionlos.domain;

import java.time.Instant;

/** Outcome of one verification check. Score is only set by the bureau. */
public record CheckResult(CheckStatus status, String ref, Integer score, Instant at) {

    public static CheckResult passed(String ref, Instant at) {
        return new CheckResult(CheckStatus.PASSED, ref, null, at);
    }

    public static CheckResult scored(String ref, int score, Instant at) {
        return new CheckResult(CheckStatus.PASSED, ref, score, at);
    }

    public static CheckResult hit(String ref, Instant at) {
        return new CheckResult(CheckStatus.HIT, ref, null, at);
    }

    public static CheckResult flagged(String ref, Instant at) {
        return new CheckResult(CheckStatus.FLAGGED, ref, null, at);
    }

    public static CheckResult skipped() {
        return new CheckResult(CheckStatus.SKIPPED, null, null, null);
    }

    public boolean blocksApplication() {
        return status == CheckStatus.HIT || status == CheckStatus.FAILED;
    }
}

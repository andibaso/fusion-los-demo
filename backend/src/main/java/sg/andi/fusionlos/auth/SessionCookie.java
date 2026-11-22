package sg.andi.fusionlos.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Stateless session: the cookie holds "userId.expiry.signature", signed with HMAC-SHA256.
 * Nothing to store server side, so the API can restart without logging anyone out
 * (as long as LOS_SESSION_SECRET stays the same).
 */
@Component
@Profile("api")
public class SessionCookie {

    public static final String NAME = "los_session";
    private static final Duration LIFETIME = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(SessionCookie.class);

    private final byte[] secret;

    public SessionCookie(@Value("${los.auth.session-secret:}") String configured) {
        if (configured.isBlank()) {
            log.warn("LOS_SESSION_SECRET not set: using a random key, sessions end when the API restarts");
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        } else {
            secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public ResponseCookie issue(DemoUser user) {
        long expires = Instant.now().plus(LIFETIME).getEpochSecond();
        String payload = user.id() + "." + expires;
        return cookie(payload + "." + sign(payload), LIFETIME);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    /** The signed-in user, if the cookie is present, untampered and not expired. */
    public Optional<DemoUser> read(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName())) return verify(cookie.getValue());
        }
        return Optional.empty();
    }

    private Optional<DemoUser> verify(String value) {
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0) return Optional.empty();
        String payload = value.substring(0, lastDot);
        byte[] given = value.substring(lastDot + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, sign(payload).getBytes(StandardCharsets.UTF_8))) return Optional.empty();

        int split = payload.lastIndexOf('.'); // user IDs contain dots too, so split from the right
        if (split < 0) return Optional.empty();
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(payload.substring(split + 1))) return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return DemoUser.find(payload.substring(0, split));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign session", e);
        }
    }

    private static ResponseCookie cookie(String value, Duration maxAge) {
        // HTTPS ends upstream of the app, so Secure is left off; the cookie is HttpOnly and same site.
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}

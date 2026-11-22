package sg.andi.fusionlos.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Sign in: pick one of the four sample users. If LOS_LOGIN_PASSWORD is set,
 * it is asked for as well, which keeps casual visitors out of the public demo.
 */
@RestController
@Profile("api")
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginOptions(boolean passwordRequired, List<DemoUser> users) {}

    public record LoginRequest(String userId, String password) {}

    private final SessionCookie sessions;
    private final String demoPassword;

    public AuthController(SessionCookie sessions, @Value("${los.auth.login-password:}") String demoPassword) {
        this.sessions = sessions;
        this.demoPassword = demoPassword;
    }

    @GetMapping("/options")
    public LoginOptions options() {
        return new LoginOptions(!demoPassword.isBlank(), DemoUser.ALL);
    }

    @PostMapping("/login")
    public ResponseEntity<DemoUser> login(@RequestBody LoginRequest req) {
        DemoUser user = DemoUser.find(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (!demoPassword.isBlank() && !matches(req.password(), demoPassword)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong password");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessions.issue(user).toString())
                .body(user);
    }

    @GetMapping("/me")
    public DemoUser me(HttpServletRequest request) {
        return sessions.read(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clear().toString()).build();
    }

    private static boolean matches(String given, String expected) {
        return given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}

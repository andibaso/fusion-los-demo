package sg.andi.fusionlos.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every /api call needs a session, except the auth endpoints themselves.
 * If the endpoint has @RequiresRole, the user's role must be listed.
 */
@Component
@Profile("api")
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionCookie sessions;

    public AuthInterceptor(SessionCookie sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || method.getBeanType() == AuthController.class) {
            return true;
        }
        DemoUser user = sessions.read(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
        request.setAttribute(CurrentUser.ATTRIBUTE, user);

        RequiresRole required = Optional.ofNullable(method.getMethodAnnotation(RequiresRole.class))
                .orElse(method.getBeanType().getAnnotation(RequiresRole.class));
        if (required != null && Arrays.stream(required.value()).noneMatch(role -> role == user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, user.name() + " (" + user.role() + ") cannot do this");
        }
        return true;
    }
}

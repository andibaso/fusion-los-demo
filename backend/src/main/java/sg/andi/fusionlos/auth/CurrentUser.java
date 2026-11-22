package sg.andi.fusionlos.auth;

import jakarta.servlet.http.HttpServletRequest;

/** The signed-in user for this request, put there by AuthInterceptor. */
public final class CurrentUser {

    static final String ATTRIBUTE = "los.user";

    private CurrentUser() {}

    public static DemoUser of(HttpServletRequest request) {
        return (DemoUser) request.getAttribute(ATTRIBUTE);
    }
}

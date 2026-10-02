package book.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Browser CSRF defense for cookie-authenticated API mutations.
 * Requests without an Origin header are allowed for non-browser/API clients;
 * when a browser supplies Origin, it must match the request origin exactly.
 */
@org.springframework.stereotype.Component
public final class SameOriginRequestFilter extends OncePerRequestFilter {

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isProtectedMutation(request)) {
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank() && !sameOrigin(origin, request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Cross-origin request rejected.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedMutation(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method))
                && path != null
                && path.startsWith("/api/");
    }

    private boolean sameOrigin(String origin, HttpServletRequest request) {
        try {
            URI actual = new URI(origin.trim());
            URI configured = new URI(publicUrl.trim().replaceAll("/+$", ""));
            int actualPort = effectivePort(actual.getScheme(), actual.getPort());
            int configuredPort = effectivePort(configured.getScheme(), configured.getPort());
            if (configured.getHost() != null
                    && configured.getScheme() != null
                    && configured.getHost().equalsIgnoreCase(actual.getHost())
                    && configured.getScheme().equalsIgnoreCase(actual.getScheme())
                    && configuredPort == actualPort) {
                return true;
            }
            int requestPort = effectivePort(request.getScheme(), request.getServerPort());
            return request.getScheme().equalsIgnoreCase(actual.getScheme())
                    && request.getServerName().equalsIgnoreCase(actual.getHost())
                    && requestPort == actualPort;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private int effectivePort(String scheme, int port) {
        if (port != -1) return port;
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}

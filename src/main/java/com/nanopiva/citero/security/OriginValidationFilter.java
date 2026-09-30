package com.nanopiva.citero.security;

import com.nanopiva.citero.config.CorsOrigins;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Defensa CSRF para los endpoints de auth con cookie (refresh/logout): valida que Origin, o en
 * su defecto Referer, esté en la allowlist de CORS. Cubre el caso {@code SameSite=None}; con
 * {@code Lax} el navegador ya no manda la cookie cross-site.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final CorsOrigins corsOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isCookieAuthEndpoint(request) && !hasValidOrigin(request)) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCookieAuthEndpoint(HttpServletRequest request) {
        return STATE_CHANGING_METHODS.contains(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    private boolean hasValidOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return corsOrigins.isAllowed(origin);
        }

        String referer = request.getHeader("Referer");
        if (referer != null) {
            return corsOrigins.isAllowed(originOf(referer));
        }

        // Sin Origin ni Referer: no es un navegador haciendo CSRF.
        return true;
    }

    private String originOf(String referer) {
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        log.warn("Petición de auth rechazada por Origin/Referer no permitido.");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{"
                + "\"status\":403,"
                + "\"error\":\"Forbidden\","
                + "\"message\":\"Origen no permitido.\""
                + "}");
    }
}

package com.nanopiva.citero.security;

import com.nanopiva.citero.config.CorsOrigins;
import com.nanopiva.citero.util.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
@Order(-200)
@RequiredArgsConstructor
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final CorsOrigins corsOrigins;

    @Value("${citero.auth.cookie.name:citero_refresh}")
    private String refreshCookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isCookieAuthEndpoint(request) && !hasValidOrigin(request)) {
            reject(request, response);
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

        // Sin Origin ni Referer: se rechaza solo si la petición trae la cookie de
        // refresh. Un navegador siempre manda Origin en métodos con estado; si no hay
        // cookie no hay sesión que un CSRF pueda abusar (clientes no-navegador siguen
        // funcionando). Con cookie presente y sin Origin, se asume intento de CSRF.
        return !carriesRefreshCookie(request);
    }

    private boolean carriesRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
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

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("security_event=csrf_origin_rejected method={} path={}",
                request.getMethod(), request.getRequestURI());
        JsonErrorWriter.write(response, 403, "Forbidden", "Origen no permitido.", request.getRequestURI());
    }
}

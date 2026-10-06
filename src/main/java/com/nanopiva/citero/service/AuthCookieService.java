package com.nanopiva.citero.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Centraliza la creación y el borrado de la cookie del refresh token.
 *
 * <p>La cookie es {@code HttpOnly} (no accesible por JS), {@code Secure} en producción
 * y con {@code SameSite} configurable. Está acotada al path de los endpoints de auth
 * para que no viaje en cada request a la API.</p>
 *
 * <p>Si se configura {@code SameSite=None} (necesario para dominios cruzados) se exige
 * {@code Secure=true}, tal como requieren los navegadores. La defensa CSRF asociada la
 * aporta {@code OriginValidationFilter}.</p>
 */
@Service
public class AuthCookieService {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final String path;
    private final long refreshTokenTtlMs;

    public AuthCookieService(
            @Value("${citero.auth.cookie.name}") String cookieName,
            @Value("${citero.auth.cookie.secure}") boolean secure,
            @Value("${citero.auth.cookie.same-site}") String sameSite,
            @Value("${citero.auth.cookie.domain:}") String domain,
            @Value("${citero.auth.cookie.path}") String path,
            @Value("${citero.auth.refresh-token-ttl-ms}") long refreshTokenTtlMs) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = normalizeSameSite(sameSite);
        this.domain = domain;
        this.path = path;
        this.refreshTokenTtlMs = refreshTokenTtlMs;

        if ("None".equalsIgnoreCase(this.sameSite) && !secure) {
            throw new IllegalStateException(
                    "citero.auth.cookie.same-site=None requiere citero.auth.cookie.secure=true: "
                            + "los navegadores descartan una cookie SameSite=None sin Secure.");
        }
    }

    private static String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> throw new IllegalStateException(
                    "Valor inválido para citero.auth.cookie.same-site: '" + value
                            + "'. Usá Lax, Strict o None.");
        };
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = buildCookie(rawToken, Duration.ofMillis(refreshTokenTtlMs));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = buildCookie("", Duration.ZERO);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}

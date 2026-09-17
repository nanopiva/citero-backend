package com.nanopiva.citero.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Centraliza la creación y el borrado de la cookie del refresh token.
 *
 * <p>La cookie es {@code HttpOnly} (no accesible por JS), {@code Secure} en producción
 * y con {@code SameSite} configurable. Está acotada al path de los endpoints de auth
 * para que no viaje en cada request a la API.</p>
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
        this.sameSite = sameSite;
        this.domain = domain;
        this.path = path;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
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

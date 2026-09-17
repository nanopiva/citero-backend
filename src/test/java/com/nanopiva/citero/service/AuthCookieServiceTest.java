package com.nanopiva.citero.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de los flags de la cookie de refresh emitida/limpiada por
 * {@link AuthCookieService}.
 */
class AuthCookieServiceTest {

    private static final long TTL_MS = 3_600_000L;

    private AuthCookieService cookieService(boolean secure) {
        return new AuthCookieService("citero_refresh", secure, "Lax", "", "/api/auth", TTL_MS);
    }

    @Test
    void setRefreshCookieIncluyeLosFlagsDeSeguridad() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService(false).setRefreshCookie(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header, "Debe emitirse el header Set-Cookie");
        assertTrue(header.contains("citero_refresh=raw-token"), "La cookie debe llevar el nombre y valor esperados");
        assertTrue(header.contains("HttpOnly"), "La cookie debe ser HttpOnly (no accesible por JS)");
        assertTrue(header.contains("Path=/api/auth"), "La cookie debe estar acotada al path de auth");
        assertTrue(header.contains("SameSite=Lax"), "La cookie debe declarar SameSite");
        assertTrue(header.contains("Max-Age=3600"), "El Max-Age debe corresponder al TTL configurado");
        assertFalse(header.contains("Secure"), "Con secure=false no debe marcarse Secure");
    }

    @Test
    void secureSeAgregaCuandoEstaHabilitado() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService(true).setRefreshCookie(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Secure"), "Con secure=true la cookie debe marcarse Secure");
    }

    @Test
    void domainSeAgregaCuandoEstaConfigurado() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthCookieService withDomain =
                new AuthCookieService("citero_refresh", false, "Lax", ".citero.app", "/api/auth", TTL_MS);

        withDomain.setRefreshCookie(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Domain=.citero.app"), "El dominio configurado debe incluirse");
    }

    @Test
    void clearRefreshCookieLaExpira() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService(false).clearRefreshCookie(response);

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header, "Debe emitirse el header Set-Cookie de limpieza");
        assertTrue(header.contains("citero_refresh="), "Debe limpiarse el valor de la cookie");
        assertTrue(header.contains("Max-Age=0"), "La cookie debe expirar de inmediato");
        assertTrue(header.contains("HttpOnly"), "La cookie de limpieza conserva HttpOnly");
    }

    @Test
    void getCookieNameDevuelveElNombreConfigurado() {
        assertEquals("citero_refresh", cookieService(false).getCookieName());
    }
}

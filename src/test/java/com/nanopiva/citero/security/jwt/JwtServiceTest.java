package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios de {@link JwtService}. Se instancia el servicio a mano y se
 * inyectan sus campos {@code @Value} con ReflectionTestUtils para no depender
 * del contexto de Spring.
 */
class JwtServiceTest {

    // Mismo secreto dummy que application-test.properties (Base64 de 64 bytes).
    private static final String SECRET =
            "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==";
    // Otro secreto del mismo largo (64 bytes) para firmar tokens ajenos.
    private static final String OTHER_SECRET =
            "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYg==";
    private static final long TTL_MS = 900_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = newJwtService(SECRET, TTL_MS);
    }

    private JwtService newJwtService(String secret, long ttlMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "jwtSecret", secret);
        ReflectionTestUtils.setField(service, "jwtExpirationMs", ttlMs);
        return service;
    }

    private UserDetailsImpl userDetails(Long id, String email) {
        return new UserDetailsImpl(id, email, "pw", "555", Collections.emptyList());
    }

    @Test
    void tokenValidoSeGeneraYExponeSusClaims() {
        String email = "jwt-" + System.nanoTime() + "@test.com";
        UserDetailsImpl details = userDetails(42L, email);

        String token = jwtService.generateTokenFromUserDetails(details);

        assertNotNull(token, "El token no debe ser nulo");
        assertFalse(token.isBlank(), "El token no debe estar vacío");
        assertTrue(jwtService.validateToken(token), "El token recién generado debe ser válido");
        assertEquals(42L, jwtService.extractUserId(token), "El subject debe contener el id del usuario");
        assertEquals(email, jwtService.extractEmail(token), "El claim email debe coincidir");

        Date expiration = jwtService.getExpirationDate(token);
        assertNotNull(expiration, "El token debe tener fecha de expiración");
        assertTrue(expiration.after(new Date()), "La expiración debe estar en el futuro");
    }

    @Test
    void tokenAlteradoEsRechazado() {
        UserDetailsImpl details = userDetails(1L, "alterado@test.com");
        String token = jwtService.generateTokenFromUserDetails(details);
        String foreignToken = newJwtService(OTHER_SECRET, TTL_MS).generateTokenFromUserDetails(details);

        String[] valid = token.split("\\.");
        String[] foreign = foreignToken.split("\\.");
        // Header y payload originales + firma de otro token => firma inválida garantizada.
        String tampered = valid[0] + "." + valid[1] + "." + foreign[2];

        assertFalse(jwtService.validateToken(tampered),
                "Un token con firma alterada debe rechazarse devolviendo false");
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService expiredService = newJwtService(SECRET, -1_000L);

        String expiredToken = expiredService.generateTokenFromUserDetails(userDetails(7L, "expirado@test.com"));

        assertFalse(jwtService.validateToken(expiredToken), "Un token expirado debe ser rechazado");
    }

    @Test
    void tokenMalformadoEsRechazado() {
        assertFalse(jwtService.validateToken("esto.no.es.un.jwt"), "Un token malformado debe ser rechazado");
        assertFalse(jwtService.validateToken(""), "Un token vacío debe ser rechazado");
    }

    @Test
    void tokenFirmadoConOtroSecretoEsRechazado() {
        JwtService otroServicio = newJwtService(OTHER_SECRET, TTL_MS);

        String token = otroServicio.generateTokenFromUserDetails(userDetails(9L, "otro@test.com"));

        assertFalse(jwtService.validateToken(token),
                "Un token firmado con otro secreto debe rechazarse devolviendo false");
    }
}

package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.security.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    private static final String ISSUER = "citero";
    private static final String AUDIENCE = "citero-app";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = newJwtService(SECRET, TTL_MS);
    }

    private JwtService newJwtService(String secret, long ttlMs) {
        return newJwtService(secret, ttlMs, ISSUER, AUDIENCE);
    }

    private JwtService newJwtService(String secret, long ttlMs, String issuer, String audience) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "jwtSecret", secret);
        ReflectionTestUtils.setField(service, "jwtExpirationMs", ttlMs);
        ReflectionTestUtils.setField(service, "issuer", issuer);
        ReflectionTestUtils.setField(service, "audience", audience);
        return service;
    }

    private UserDetailsImpl userDetails(Long id, String email) {
        return new UserDetailsImpl(id, email, "pw", Collections.emptyList());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

        Claims claims = parseClaims(token);
        assertEquals(email, claims.get("email", String.class), "El claim email debe coincidir");
        assertNotNull(claims.getExpiration(), "El token debe tener fecha de expiración");
        assertTrue(claims.getExpiration().after(new Date()), "La expiración debe estar en el futuro");
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

    @Test
    void tokenConOtroIssuerOAudienceEsRechazado() {
        UserDetailsImpl details = userDetails(5L, "contexto@test.com");

        String otherIssuer = newJwtService(SECRET, TTL_MS, "otro-emisor", AUDIENCE)
                .generateTokenFromUserDetails(details);
        String otherAudience = newJwtService(SECRET, TTL_MS, ISSUER, "otra-app")
                .generateTokenFromUserDetails(details);

        assertFalse(jwtService.validateToken(otherIssuer), "Otro issuer debe rechazarse");
        assertFalse(jwtService.validateToken(otherAudience), "Otra audience debe rechazarse");
    }

    @Test
    void tokenConAlgNoneEsRechazado() {
        assertFalse(jwtService.validateToken(unsecuredToken("access")),
                "Un token sin firma (alg=none) debe rechazarse");
    }

    @Test
    void tokenConPropositoDistintoEsRechazado() {
        String linkToken = signedToken("appointment-manage", true);

        assertFalse(jwtService.validateToken(linkToken),
                "Un token con otro propósito (link público) no debe servir como access token");
    }

    @Test
    void tokenFirmadoConHS256EsRechazado() {
        String token = signedToken("access", false);

        assertFalse(jwtService.validateToken(token),
                "El algoritmo esperado es HS512: HS256 debe rechazarse aunque el secreto sea el mismo");
    }

    /** Firma un token con el secreto de test; {@code hs512=false} fuerza HS256 (algorithm confusion). */
    private String signedToken(String purpose, boolean hs512) {
        return Jwts.builder()
                .subject("1")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("purpose", purpose)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL_MS))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)),
                        hs512 ? Jwts.SIG.HS512 : Jwts.SIG.HS256)
                .compact();
    }

    private String unsecuredToken(String purpose) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString((
                "{\"sub\":\"1\",\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE
                        + "\",\"purpose\":\"" + purpose + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }
}

package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.security.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/** Genera y verifica los JWT de sesión (access token y desafío MFA). */
@Slf4j
@Service
public class JwtService {

    private static final String ACCESS_PURPOSE = "access";
    private static final String MFA_PURPOSE = "mfa";
    private static final String ACCESS_ALGORITHM = "HS512";
    private static final int MIN_SECRET_BYTES = 64; // HS512 exige >= 512 bits

    @Value("${citero.jwt.secret}")
    private String jwtSecret;

    @Value("${citero.jwt.issuer:citero}")
    private String issuer;

    @Value("${citero.jwt.audience:citero-app}")
    private String audience;

    @Value("${citero.auth.access-token-ttl-ms}")
    private long jwtExpirationMs;

    @Value("${citero.auth.mfa.challenge-ttl-ms:300000}")
    private long mfaChallengeTtlMs;

    /** Falla al arrancar (no en el primer login) si el secreto falta o es corto para HS512. */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "citero.jwt.secret no está configurado. Definí CITERO_JWT_SECRET (Base64, >= 64 bytes).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception ex) {
            throw new IllegalStateException("citero.jwt.secret debe estar codificado en Base64.", ex);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("citero.jwt.secret debe decodificar a al menos "
                    + MIN_SECRET_BYTES + " bytes para HS512; actual: " + keyBytes.length + " bytes.");
        }
    }

    public String generateTokenFromUserDetails(UserDetailsImpl userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getId().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("email", userDetails.getEmail())
                .claim("purpose", ACCESS_PURPOSE)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Token de desafío MFA: se emite tras validar la contraseña cuando la cuenta tiene TOTP
     * activo y sirve para completar el segundo factor. Vida corta y propósito distinto del
     * access token.
     */
    public String generateMfaToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("purpose", MFA_PURPOSE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + mfaChallengeTtlMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    /** Extrae el id de usuario de un token de desafío MFA. Lanza si no lo es o expiró. */
    public Long parseMfaToken(String token) {
        Claims claims = parse(token).getPayload();
        if (!MFA_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("El token no es un desafío MFA válido.");
        }
        return Long.parseLong(claims.getSubject());
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Token JWT inválido: {}", ex.getMessage());
        }
        return false;
    }

    private Jws<Claims> parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);

        // Fija el algoritmo esperado: nunca se elige a partir del header del token.
        if (!ACCESS_ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Algoritmo de firma no permitido.");
        }
        return jws;
    }

    private Claims parseClaims(String token) {
        Claims claims = parse(token).getPayload();
        // Distingue el access token de otros JWT firmados con el mismo secreto (p. ej. link público o MFA).
        if (!ACCESS_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("El token no es un access token válido.");
        }
        return claims;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.security.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Servicio responsable de la generación, validación y parsing de tokens JWT.
 *
 * Utiliza la biblioteca JJWT para firmar los tokens con un algoritmo HMAC-SHA
 * y extraer claims como el ID de usuario, email y roles.
 */
@Slf4j
@Service
public class JwtService {

    private static final String ACCESS_PURPOSE = "access";
    private static final int MIN_SECRET_BYTES = 64; // HS512 exige >= 512 bits

    @Value("${citero.jwt.secret}")
    private String jwtSecret;

    @Value("${citero.jwt.issuer:citero}")
    private String issuer;

    @Value("${citero.jwt.audience:citero-app}")
    private String audience;

    @Value("${citero.auth.access-token-ttl-ms}")
    private long jwtExpirationMs;

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

    /**
     * Genera un token JWT a partir de la autenticación actual.
     *
     * @param authentication la autenticación de Spring Security
     * @return el token JWT firmado
     */
    public String generateToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return generateTokenFromUserDetails(userPrincipal);
    }

    /**
     * Genera un token JWT a partir de los datos de un usuario.
     *
     * @param userDetails los detalles del usuario
     * @return el token JWT firmado
     */
    public String generateTokenFromUserDetails(UserDetailsImpl userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getId().toString())
                .issuer(issuer)
                .setAudience(audience)
                .claim("email", userDetails.getEmail())
                .claim("purpose", ACCESS_PURPOSE)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Extrae el ID de usuario (claim "sub") del token JWT.
     *
     * @param token el token JWT
     * @return el ID del usuario como Long
     */
    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extrae el email del usuario del token JWT.
     *
     * @param token el token JWT
     * @return el email del usuario
     */
    public String extractEmail(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Valida un token JWT verificando su firma y expiración.
     *
     * @param authToken el token a validar
     * @return true si el token es válido, false en caso contrario
     */
    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Token JWT inválido: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Obtiene la fecha de expiración de un token JWT.
     *
     * @param token el token JWT
     * @return la fecha de expiración
     */
    public Date getExpirationDate(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * Parsea los claims de un token JWT.
     *
     * @param token el token JWT
     * @return los claims contenidos en el token
     */
    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Distingue el access token de otros JWT firmados con el mismo secreto (p. ej. el link público).
        if (!ACCESS_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("El token no es un access token válido.");
        }
        return claims;
    }

    /**
     * Obtiene la clave de firma a partir del secreto configurado.
     *
     * @return la clave secreta para firmar/verificar tokens
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

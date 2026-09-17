package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.security.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

    @Value("${citero.jwt.secret}")
    private String jwtSecret;

    @Value("${citero.auth.access-token-ttl-ms}")
    private long jwtExpirationMs;

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
                .claim("email", userDetails.getEmail())
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
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
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
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.RefreshToken;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida de los refresh tokens: emisión, rotación con detección
 * de reuso y revocación.
 *
 * <p>El token en claro se entrega al cliente (vía cookie HttpOnly) pero solo se
 * persiste su hash SHA-256. La rotación invalida el token anterior y emite uno nuevo
 * dentro de la misma familia de sesión; si se presenta un token ya revocado se asume
 * robo y se revoca toda la familia.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${citero.auth.refresh-token-ttl-ms}")
    private long refreshTokenTtlMs;

    /** Par (token en claro, usuario) resultante de emitir/rotar. */
    public record RefreshTokenPair(String rawToken, User user) {
    }

    /**
     * Emite un refresh token nuevo, iniciando una nueva familia de sesión.
     */
    @Transactional
    public RefreshTokenPair issue(User user, String userAgent, String ipAddress) {
        return issueInSession(user, UUID.randomUUID().toString(), userAgent, ipAddress);
    }

    /**
     * Rota un refresh token: valida el actual, lo revoca y emite uno nuevo de la misma
     * familia.
     *
     * <p>Devuelve {@link Optional#empty()} si el token no existe, expiró o fue reusado.
     * En el caso de reuso se revoca toda la familia. Importante: este método <b>no lanza
     * excepción</b> tras revocar, para que la transacción confirme la revocación; el
     * llamador es quien traduce el {@code empty} a un 401.</p>
     */
    @Transactional
    public Optional<RefreshTokenPair> rotate(String rawToken, String userAgent, String ipAddress) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken existing = found.get();

        if (Boolean.TRUE.equals(existing.getRevoked())) {
            log.warn("Reuso de refresh token detectado (usuario {}, sesión {}). Se revoca la familia completa.",
                    existing.getUser().getId(), existing.getSessionId());
            refreshTokenRepository.revokeBySessionId(existing.getSessionId());
            return Optional.empty();
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            return Optional.empty();
        }

        RefreshTokenPair pair = issueInSession(existing.getUser(), existing.getSessionId(), userAgent, ipAddress);
        existing.setRevoked(true);
        existing.setReplacedByHash(hash(pair.rawToken()));
        refreshTokenRepository.save(existing);
        return Optional.of(pair);
    }

    /**
     * Revoca la familia de sesión a la que pertenece el token (logout).
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token ->
                refreshTokenRepository.revokeBySessionId(token.getSessionId()));
    }

    /**
     * Elimina los refresh tokens expirados (housekeeping diario).
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupExpired() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Refresh tokens expirados eliminados: {}", deleted);
        }
    }

    private RefreshTokenPair issueInSession(User user, String sessionId, String userAgent, String ipAddress) {
        String rawToken = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .sessionId(sessionId)
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenTtlMs)))
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ipAddress, 45))
                .revoked(false)
                .build();
        refreshTokenRepository.save(token);
        return new RefreshTokenPair(rawToken, user);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

package com.nanopiva.citero.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * Token firmado para los links públicos de gestión de turnos (email). Es determinista (se
 * regenera desde el id y la hora de inicio) y vence a las horas de gracia tras el inicio,
 * así no queda un ID adivinable en la URL.
 */
@Slf4j
@Service
public class PublicLinkTokenService {

    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_APPOINTMENT_MANAGE = "appointment-manage";

    @Value("${citero.public-link.secret}")
    private String linkSecret;

    @Value("${citero.public-link.grace-hours:720}")
    private long graceHours;

    /** Falla al arrancar si el secreto del link público no sirve para HS256 (>= 32 bytes). */
    @PostConstruct
    void validateSecret() {
        if (linkSecret == null || linkSecret.isBlank()) {
            throw new IllegalStateException("citero.public-link.secret no está configurado.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(linkSecret);
        } catch (Exception ex) {
            throw new IllegalStateException("citero.public-link.secret debe estar codificado en Base64.", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("citero.public-link.secret debe decodificar a al menos "
                    + "32 bytes para HS256; actual: " + keyBytes.length + " bytes.");
        }
    }

    public String generate(Long appointmentId, LocalDateTime startTime, String timezone) {
        Instant expiresAt = expirationFor(startTime, timezone);
        return Jwts.builder()
                .subject(appointmentId.toString())
                .claim(CLAIM_PURPOSE, PURPOSE_APPOINTMENT_MANAGE)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Optional<Long> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!PURPOSE_APPOINTMENT_MANAGE.equals(claims.get(CLAIM_PURPOSE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token de link público inválido: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Instant expirationFor(LocalDateTime startTime, String timezone) {
        ZoneId zone = resolveZone(timezone);
        Instant start = (startTime != null ? startTime.atZone(zone) : ZonedDateTime.now(zone)).toInstant();
        return start.plus(Duration.ofHours(graceHours));
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return ZoneId.systemDefault();
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(linkSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

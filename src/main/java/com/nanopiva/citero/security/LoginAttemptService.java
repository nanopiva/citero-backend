package com.nanopiva.citero.security;

import com.nanopiva.citero.util.Emails;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Detección de fuerza bruta por cuenta: tras {@code maxFailures} fallos consecutivos, bloquea
 * temporalmente el login de esa cuenta. El estado es en memoria (una sola instancia), igual que
 * el rate limiting; queda pendiente reemplazarlo por un store compartido si se escala.
 *
 * <p>Trade-off (DoS): un bloqueo por cuenta es un vector de DoS dirigido. Se mitiga con una
 * ventana de fallos y un bloqueo cortos, con el cap por IP del filtro (frena la tasa de intentos)
 * y con el hecho de que el reseteo de contraseña limpia el bloqueo, de modo que el dueño legítimo
 * siempre puede recuperar el acceso.</p>
 */
@Slf4j
@Component
public class LoginAttemptService {

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockDuration;

    // Backstop de memoria: acota las cuentas con fallos registrados.
    @Value("${citero.rate-limit.max-keys:100000}")
    private int maxEntries = 100_000;

    // Inyectable sólo desde tests para controlar el paso del tiempo.
    private LongSupplier clock = System::currentTimeMillis;

    public LoginAttemptService(
            @Value("${citero.auth.lockout.max-failures:5}") int maxFailures,
            @Value("${citero.auth.lockout.failure-window-minutes:15}") long failureWindowMinutes,
            @Value("${citero.auth.lockout.lock-minutes:15}") long lockMinutes) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failureWindow = Duration.ofMinutes(Math.max(1, failureWindowMinutes));
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    /** Avisa que el lockout es local al proceso (una sola instancia). */
    @PostConstruct
    void warnAboutLocalState() {
        log.warn("Lockout de login con estado EN MEMORIA (una sola instancia). "
                + "Si se despliega más de una instancia, reemplazar por un store compartido.");
    }

    /** true si la cuenta está temporalmente bloqueada. */
    public boolean isLocked(String email) {
        Attempt attempt = attempts.get(key(email));
        return attempt != null && attempt.isLocked(clock.getAsLong());
    }

    /** Segundos restantes de bloqueo (0 si no está bloqueada). */
    public long retryAfterSeconds(String email) {
        Attempt attempt = attempts.get(key(email));
        return attempt == null ? 0 : attempt.remainingSeconds(clock.getAsLong());
    }

    /**
     * Registra un fallo de login. Al alcanzar {@code maxFailures} dentro de la ventana, bloquea
     * la cuenta. Los fallos previos fuera de la ventana no cuentan.
     */
    public void recordFailure(String email) {
        String key = key(email);
        // Backstop de memoria: si el mapa está lleno y es una cuenta nueva, no se agrega
        // (fail-open acotado; el rate limiting por IP sigue aplicando).
        if (attempts.size() >= maxEntries && !attempts.containsKey(key)) {
            return;
        }
        attempts.compute(key, (k, existing) -> {
            long now = clock.getAsLong();
            Attempt attempt = existing;
            if (attempt == null || attempt.isExpired(now, failureWindow)) {
                attempt = new Attempt(now);
            }
            if (attempt.isLocked(now)) {
                // Ya bloqueada: no se extiende el bloqueo con nuevos intentos.
                return attempt;
            }
            attempt.failures++;
            attempt.lastFailureAt = now;
            if (attempt.failures >= maxFailures) {
                attempt.lockedUntil = now + lockDuration.toMillis();
            }
            return attempt;
        });
    }

    /** Limpia el estado tras un login exitoso (o al recuperar la cuenta). */
    public void reset(String email) {
        attempts.remove(key(email));
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = clock.getAsLong();
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now, failureWindow));
    }

    private static String key(String email) {
        return Emails.normalize(email);
    }

    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    private static final class Attempt {

        private volatile long lastFailureAt;
        private volatile int failures;
        private volatile long lockedUntil;

        private Attempt(long now) {
            this.lastFailureAt = now;
        }

        private boolean isLocked(long now) {
            return lockedUntil > now;
        }

        private long remainingSeconds(long now) {
            long remaining = lockedUntil - now;
            return remaining <= 0 ? 0 : remaining / 1000;
        }

        /** Sin fallos dentro de la ventana ni bloqueo vigente, el registro se considera vencido. */
        private boolean isExpired(long now, Duration failureWindow) {
            if (isLocked(now)) {
                return false;
            }
            return now - lastFailureAt > failureWindow.toMillis();
        }
    }
}

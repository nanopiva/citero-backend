package com.nanopiva.citero.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting en memoria con ventana fija (implementación por defecto de {@link RateLimitStore}).
 * Sirve para una sola instancia; con varias hay que usar un store compartido.
 */
@Slf4j
@Component
public class RateLimiter implements RateLimitStore {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    // Backstop de memoria: acota la cantidad de claves para no crecer sin límite ante un flood
    // de IPs/claves distintas. Al superarlo se rechazan claves nuevas (fail-closed) hasta que la
    // limpieza programada libere las vencidas.
    @Value("${citero.rate-limit.max-keys:100000}")
    private int maxKeys = 100_000;

    /** Avisa que el conteo es local al proceso (una sola instancia). */
    @PostConstruct
    void warnAboutLocalState() {
        log.warn("Rate limiting con estado EN MEMORIA (una sola instancia). "
                + "Si se despliega más de una instancia o serverless, reemplazar por un store compartido.");
    }

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
        if (windows.size() >= maxKeys && !windows.containsKey(key)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || now >= existing.resetAt) {
                return new Window(now + window.toMillis());
            }
            existing.count++;
            return existing;
        });
        return current.count <= limit;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAt);
    }

    private static final class Window {

        private final long resetAt;
        private int count;

        private Window(long resetAt) {
            this.resetAt = resetAt;
            this.count = 1;
        }
    }
}

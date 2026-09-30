package com.nanopiva.citero.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

    /** Avisa que el conteo es local al proceso (una sola instancia). */
    @PostConstruct
    void warnAboutLocalState() {
        log.warn("Rate limiting con estado EN MEMORIA (una sola instancia). "
                + "Si se despliega más de una instancia o serverless, reemplazar por un store compartido.");
    }

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
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

    @Scheduled(fixedDelay = 600_000)
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

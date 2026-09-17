package com.nanopiva.citero.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa en memoria con ventana fija.
 *
 * <p>Es una defensa básica contra fuerza bruta y abuso. Al ser estado en memoria, cada
 * instancia del backend lleva su propio conteo; para un despliegue con varias instancias
 * o balanceo horizontal conviene respaldarlo con un almacén compartido (p. ej. Redis).</p>
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

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

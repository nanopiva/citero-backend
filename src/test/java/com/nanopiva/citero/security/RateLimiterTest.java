package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests unitarios del rate limiter en memoria. */
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    void respetaElLimitePorVentana() {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.tryConsume("k", 2, WINDOW));
        assertTrue(limiter.tryConsume("k", 2, WINDOW));
        assertFalse(limiter.tryConsume("k", 2, WINDOW), "El tercer consumo supera el límite");
    }

    @Test
    void backstopAlAlcanzarElMaximoDeClaves() {
        RateLimiter limiter = new RateLimiter();
        ReflectionTestUtils.setField(limiter, "maxKeys", 2);

        assertTrue(limiter.tryConsume("k1", 5, WINDOW));
        assertTrue(limiter.tryConsume("k2", 5, WINDOW));
        // Con el mapa lleno, una clave nueva se rechaza (fail-closed)...
        assertFalse(limiter.tryConsume("k3", 5, WINDOW));
        // ...pero una clave existente sigue funcionando.
        assertTrue(limiter.tryConsume("k1", 5, WINDOW));
    }

    @Test
    void laVentanaSeReinicia() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.tryConsume("k", 1, Duration.ofMillis(20)));
        assertFalse(limiter.tryConsume("k", 1, Duration.ofMillis(20)));

        Thread.sleep(200);
        assertTrue(limiter.tryConsume("k", 1, Duration.ofMillis(20)), "La ventana debe reiniciarse");
    }
}

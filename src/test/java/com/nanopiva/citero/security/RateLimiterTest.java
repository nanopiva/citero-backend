package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void permiteHastaElLimiteYLuegoBloquea() {
        assertTrue(limiter.tryConsume("ip-1", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.tryConsume("ip-1", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.tryConsume("ip-1", 3, Duration.ofMinutes(1)));
        assertFalse(limiter.tryConsume("ip-1", 3, Duration.ofMinutes(1)),
                "La cuarta solicitud supera el límite de 3");
    }

    @Test
    void clavesDistintasTienenContadoresIndependientes() {
        assertTrue(limiter.tryConsume("ip-a", 1, Duration.ofMinutes(1)));
        assertFalse(limiter.tryConsume("ip-a", 1, Duration.ofMinutes(1)));
        assertTrue(limiter.tryConsume("ip-b", 1, Duration.ofMinutes(1)),
                "Otra clave no debe verse afectada por el contador de ip-a");
    }
}

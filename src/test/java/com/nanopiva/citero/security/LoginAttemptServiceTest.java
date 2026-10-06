package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests unitarios del lockout por cuenta, con reloj controlado. */
class LoginAttemptServiceTest {

    private static final long MINUTE = 60_000L;

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private LoginAttemptService newService(int maxFailures, long windowMin, long lockMin) {
        LoginAttemptService service = new LoginAttemptService(maxFailures, windowMin, lockMin);
        service.setClock(now::get);
        return service;
    }

    @Test
    void noBloqueaAntesDelMaximo() {
        LoginAttemptService service = newService(5, 15, 15);

        for (int i = 0; i < 4; i++) {
            service.recordFailure("ana@test.com");
        }

        assertFalse(service.isLocked("ana@test.com"));
    }

    @Test
    void bloqueaAlAlcanzarElMaximo() {
        LoginAttemptService service = newService(5, 15, 15);

        for (int i = 0; i < 5; i++) {
            service.recordFailure("ana@test.com");
        }

        assertTrue(service.isLocked("ana@test.com"));
        assertTrue(service.retryAfterSeconds("ana@test.com") > 0);
    }

    @Test
    void elResetDesbloquea() {
        LoginAttemptService service = newService(5, 15, 15);
        for (int i = 0; i < 5; i++) {
            service.recordFailure("ana@test.com");
        }

        service.reset("ana@test.com");

        assertFalse(service.isLocked("ana@test.com"));
    }

    @Test
    void elBloqueoExpira() {
        LoginAttemptService service = newService(5, 15, 15);
        for (int i = 0; i < 5; i++) {
            service.recordFailure("ana@test.com");
        }
        assertTrue(service.isLocked("ana@test.com"));

        now.addAndGet(16 * MINUTE);

        assertFalse(service.isLocked("ana@test.com"));
    }

    @Test
    void losFallosViejosFueraDeLaVentanaNoCuentan() {
        LoginAttemptService service = newService(5, 15, 15);
        for (int i = 0; i < 4; i++) {
            service.recordFailure("ana@test.com");
        }

        now.addAndGet(16 * MINUTE);
        service.recordFailure("ana@test.com");

        assertFalse(service.isLocked("ana@test.com"), "Un fallo aislado no debe bloquear");
    }

    @Test
    void noExtiendeElBloqueoConNuevosIntentos() {
        LoginAttemptService service = newService(5, 15, 15);
        for (int i = 0; i < 5; i++) {
            service.recordFailure("ana@test.com");
        }
        long remainingAfterLock = service.retryAfterSeconds("ana@test.com");

        now.addAndGet(MINUTE);
        service.recordFailure("ana@test.com");

        assertTrue(service.retryAfterSeconds("ana@test.com") <= remainingAfterLock,
                "Los intentos durante el bloqueo no deben extenderlo");
    }
}

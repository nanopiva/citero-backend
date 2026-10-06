package com.nanopiva.citero.config;

import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderConfigTest extends IntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void encodeNoGuardaLaPasswordEnClaro() {
        String encoded = passwordEncoder.encode("secret123");

        assertNotEquals("secret123", encoded, "El hash no debe coincidir con la password");
        assertTrue(encoded.startsWith("$2"), "BCrypt produce hashes que empiezan con $2");
    }

    @Test
    void matchesValidaLaPasswordCorrectaYRechazaLaIncorrecta() {
        String encoded = passwordEncoder.encode("secret123");

        assertTrue(passwordEncoder.matches("secret123", encoded), "La password correcta debe validar");
        assertFalse(passwordEncoder.matches("otra-password", encoded), "La password incorrecta no debe validar");
    }

    @Test
    void dosHashesDeLaMismaPasswordSonDistintosPorElSalt() {
        String primero = passwordEncoder.encode("secret123");
        String segundo = passwordEncoder.encode("secret123");

        assertNotEquals(primero, segundo, "BCrypt usa salt aleatorio en cada encode");
        assertTrue(passwordEncoder.matches("secret123", primero));
        assertTrue(passwordEncoder.matches("secret123", segundo));
    }
}

package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vectores de prueba del RFC 6238 (SHA1, 8 dígitos) más verificación con ventana y Base32.
 */
class TotpServiceTest {

    // RFC 6238: clave ASCII "12345678901234567890".
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpService service = new TotpService(6);

    @Test
    void cumpleLosVectoresDelRfc6238() {
        assertEquals("94287082", TotpService.codeFromKeyForTest(RFC_KEY, 59L, 8));
        assertEquals("07081804", TotpService.codeFromKeyForTest(RFC_KEY, 1111111109L, 8));
        assertEquals("14050471", TotpService.codeFromKeyForTest(RFC_KEY, 1111111111L, 8));
        assertEquals("89005924", TotpService.codeFromKeyForTest(RFC_KEY, 1234567890L, 8));
        assertEquals("69279037", TotpService.codeFromKeyForTest(RFC_KEY, 2000000000L, 8));
        assertEquals("65353130", TotpService.codeFromKeyForTest(RFC_KEY, 20000000000L, 8));
    }

    @Test
    void base32HaceRoundTrip() {
        byte[] data = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String encoded = TotpService.base32EncodeForTest(data);
        // Decodificar vía codeAt no falla y reproduce el mismo código que con la clave cruda.
        long epoch = 59L;
        assertEquals(TotpService.codeFromKeyForTest(data, epoch, 6), service.codeAt(encoded, epoch, 6));
    }

    @Test
    void verificaElCodigoActual() {
        String secret = service.generateSecret();
        long now = System.currentTimeMillis() / 1000;
        String code = service.codeAt(secret, now, 6);

        assertTrue(service.verify(secret, code));
        assertFalse(service.verify(secret, "000000".equals(code) ? "111111" : "000000"));
        assertFalse(service.verify(secret, "12345"), "Longitud incorrecta debe rechazarse");
        assertFalse(service.verify(secret, "abcdef"), "No numérico debe rechazarse");
    }

    @Test
    void aceptaLaVentanaDeUnPasoYRechazaMasLejos() {
        String secret = service.generateSecret();
        long now = System.currentTimeMillis() / 1000;

        assertTrue(service.verify(secret, service.codeAt(secret, now - 30, 6)), "Paso previo (±1) válido");
        assertTrue(service.verify(secret, service.codeAt(secret, now + 30, 6)), "Paso siguiente (±1) válido");
        assertFalse(service.verify(secret, service.codeAt(secret, now - 90, 6)), "Dos pasos atrás inválido");
    }

    @Test
    void elSecretoGeneradoEsUnicoYBase32() {
        String a = service.generateSecret();
        String b = service.generateSecret();

        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Z2-7]{32}"), "20 bytes => 32 chars Base32 sin padding: " + a);
    }
}

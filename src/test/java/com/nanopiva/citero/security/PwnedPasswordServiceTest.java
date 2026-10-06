package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Sin llamadas de red: se valida el hash k-Anonymity (SHA-1) y el comportamiento desactivado.
 */
class PwnedPasswordServiceTest {

    @Test
    void desactivadoNuncaReportaFiltrada() {
        PwnedPasswordService service = new PwnedPasswordService(false);
        assertFalse(service.isBreached("password"));
        assertFalse(service.isBreached(""));
        assertFalse(service.isBreached(null));
    }

    @Test
    void calculaElSha1DeLaContrasena() {
        PwnedPasswordService service = new PwnedPasswordService(false);
        // Valor de referencia conocido de SHA-1("password").
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", service.sha1Hex("password"));
    }
}

package com.nanopiva.citero.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailTextTest {

    @Test
    void eliminaCrLfYCaracteresDeControl() {
        assertEquals("Barbería X", EmailText.sanitizeHeader("Barbería\r\nX"));
        assertEquals("A B", EmailText.sanitizeHeader("A\u0000B"));
        assertEquals("Sin espacios", EmailText.sanitizeHeader("  Sin   espacios  "));
    }

    @Test
    void nullDevuelveVacio() {
        assertEquals("", EmailText.sanitizeHeader(null));
    }

    @Test
    void acotaLaLongitudDelHeader() {
        assertEquals(200, EmailText.sanitizeHeader("a".repeat(500)).length());
    }
}

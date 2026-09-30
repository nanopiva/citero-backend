package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonPasswordCheckTest {

    @Test
    void detectaComunesSinImportarMayusculasNiEspacios() {
        assertTrue(CommonPasswordCheck.isCommon("password123"));
        assertTrue(CommonPasswordCheck.isCommon("Password123"));
        assertTrue(CommonPasswordCheck.isCommon("  qwerty123  "));
        assertTrue(CommonPasswordCheck.isCommon("contraseña"));
    }

    @Test
    void noMarcaPasswordsAceptables() {
        assertFalse(CommonPasswordCheck.isCommon("miClaveSegura2026"));
        assertFalse(CommonPasswordCheck.isCommon(null));
    }
}

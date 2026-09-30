package com.nanopiva.citero.security;

import java.util.Locale;
import java.util.Set;

/** Blocklist local de contraseñas comunes (sin llamadas externas). */
public final class CommonPasswordCheck {

    private static final Set<String> COMMON = Set.of(
            "12345678", "123456789", "1234567890", "11111111", "00000000",
            "password", "password1", "password123", "contrasena", "contraseña",
            "qwerty123", "qwertyui", "1q2w3e4r", "abc12345", "iloveyou",
            "admin123", "welcome1", "123123123", "citero123", "barberia"
    );

    private CommonPasswordCheck() {
    }

    public static boolean isCommon(String password) {
        if (password == null) {
            return false;
        }
        return COMMON.contains(password.trim().toLowerCase(Locale.ROOT));
    }
}

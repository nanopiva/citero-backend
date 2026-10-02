package com.nanopiva.citero.util;

import java.util.Locale;

/** Normaliza emails para que la identidad no dependa de mayúsculas ni espacios. */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

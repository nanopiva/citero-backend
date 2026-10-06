package com.nanopiva.citero.util;

/**
 * Utilidades para no volcar PII completa en los logs. Los emails se enmascaran dejando sólo
 * la primera letra de la parte local y el dominio (p. ej. {@code ana@gmail.com -> a***@gmail.com}),
 * suficiente para operar/correlacionar sin exponer la dirección completa.
 */
public final class Logs {

    /** Clave del MDC con el id de correlación por request (ver CorrelationIdFilter). */
    public static final String REQUEST_ID = "requestId";

    private Logs() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}

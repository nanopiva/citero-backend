package com.nanopiva.citero.util;

import com.nanopiva.citero.entity.Business;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Resuelve la hora "actual" en la zona horaria del negocio.
 *
 * <p>Los turnos se guardan como hora local del negocio (wall-clock), por lo que
 * comparar contra {@code LocalDateTime.now()} usaría la zona del servidor y produciría
 * desfasajes (p. ej. 3 horas si el servidor corre en UTC y el negocio en Argentina).
 * Estos helpers usan siempre la zona configurada en el negocio.</p>
 */
public final class BusinessTime {

    public static final String DEFAULT_ZONE_ID = "America/Argentina/Buenos_Aires";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_ZONE_ID);

    private BusinessTime() {
    }

    public static ZoneId zoneOf(Business business) {
        return business == null ? DEFAULT_ZONE : zoneOf(business.getTimezone());
    }

    /**
     * Resuelve una zona IANA. Si viene null, vacía o inválida cae a la zona por
     * defecto de la app (America/Argentina/Buenos_Aires). Única fuente de verdad
     * del fallback de zona horaria.
     */
    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    public static LocalDateTime now(Business business) {
        return LocalDateTime.now(zoneOf(business));
    }

    public static LocalDate today(Business business) {
        return LocalDate.now(zoneOf(business));
    }
}

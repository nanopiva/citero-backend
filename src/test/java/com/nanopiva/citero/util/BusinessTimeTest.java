package com.nanopiva.citero.util;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessTimeTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void zonaNulaVaciaOInvalidaCaeAlDefault() {
        assertEquals(BUENOS_AIRES, BusinessTime.zoneOf((String) null));
        assertEquals(BUENOS_AIRES, BusinessTime.zoneOf(""));
        assertEquals(BUENOS_AIRES, BusinessTime.zoneOf("   "));
        assertEquals(BUENOS_AIRES, BusinessTime.zoneOf("No/Existe"));
    }

    @Test
    void zonaValidaSeRespeta() {
        assertEquals(ZoneId.of("America/New_York"), BusinessTime.zoneOf("America/New_York"));
        assertEquals(ZoneId.of("Europe/Madrid"), BusinessTime.zoneOf("Europe/Madrid"));
    }
}

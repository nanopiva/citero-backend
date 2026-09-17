package com.nanopiva.citero.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageTypeTest {

    @Test
    void logoUsaLimiteDe512() {
        assertEquals(512, ImageType.LOGO.getMaxWidth(), "El ancho máximo del logo debe ser 512");
        assertEquals(512, ImageType.LOGO.getMaxHeight(), "El alto máximo del logo debe ser 512");
    }

    @Test
    void coverUsaLimiteDe1600() {
        assertEquals(1600, ImageType.COVER.getMaxWidth(), "El ancho máximo de la portada debe ser 1600");
        assertEquals(1600, ImageType.COVER.getMaxHeight(), "El alto máximo de la portada debe ser 1600");
    }

    @Test
    void contieneExactamenteDosValores() {
        assertEquals(2, ImageType.values().length, "Deben existir exactamente LOGO y COVER");
    }

    @Test
    void valueOfResuelvePorNombre() {
        assertEquals(ImageType.LOGO, ImageType.valueOf("LOGO"));
        assertEquals(ImageType.COVER, ImageType.valueOf("COVER"));
    }
}

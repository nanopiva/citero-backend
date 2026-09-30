package com.nanopiva.citero.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSignatureTest {

    @Test
    void detectaJpegPngYWebp() {
        assertEquals(ImageSignature.JPEG,
                ImageSignature.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));

        assertEquals(ImageSignature.PNG,
                ImageSignature.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00}));

        byte[] webp = new byte[12];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertEquals(ImageSignature.WEBP, ImageSignature.detect(webp));
    }

    @Test
    void rechazaContenidoSinFirmaOTruncado() {
        assertNull(ImageSignature.detect(new byte[16]));
        assertNull(ImageSignature.detect(new byte[]{(byte) 0xFF, (byte) 0xD8}));
        assertNull(ImageSignature.detect(null));
    }
}

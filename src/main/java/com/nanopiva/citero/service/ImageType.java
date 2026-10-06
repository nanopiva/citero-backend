package com.nanopiva.citero.service;

/**
 * Tipos de imagen del negocio, con el tamaño máximo al que se
 * redimensionan al subirlas (crop "limit": nunca agranda, solo achica).
 */
public enum ImageType {
    LOGO(512, 512),
    COVER(1600, 1600);

    private final int maxWidth;
    private final int maxHeight;

    ImageType(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}

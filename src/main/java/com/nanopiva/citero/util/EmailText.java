package com.nanopiva.citero.util;

public final class EmailText {

    private static final int MAX_HEADER_LENGTH = 200;

    private EmailText() {
    }

    /** Limpia un valor para usar como asunto: quita control chars (\r\n), colapsa espacios y acota. */
    public static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_HEADER_LENGTH) {
            cleaned = cleaned.substring(0, MAX_HEADER_LENGTH);
        }
        return cleaned;
    }
}

package com.nanopiva.citero.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Orígenes permitidos por CORS, compartidos con la validación de Origin/Referer. */
@Component
public class CorsOrigins {

    private final List<String> origins;

    public CorsOrigins(@Value("${citero.cors.allowed-origins}") String allowedOriginsRaw) {
        this.origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    public List<String> list() {
        return origins;
    }

    /** {@code "null"} (iframes sandboxeados) se considera no permitido. */
    public boolean isAllowed(String origin) {
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin)) {
            return false;
        }
        return origins.contains(origin);
    }
}

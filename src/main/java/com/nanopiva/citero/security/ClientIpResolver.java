package com.nanopiva.citero.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve la IP real desde X-Forwarded-For tomando el valor que dejó el proxy de confianza
 * ({@code size - trustedProxyHops}), no el left-most que el cliente puede falsificar.
 * Requiere {@code server.forward-headers-strategy=none} para leer el header crudo.
 */
@Component
public class ClientIpResolver {

    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${citero.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    public String resolve(HttpServletRequest request) {
        if (trustedProxyHops <= 0) {
            return request.getRemoteAddr();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            List<String> values = new ArrayList<>();
            for (String part : forwardedFor.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            if (!values.isEmpty()) {
                int index = values.size() - trustedProxyHops;
                if (index < 0) {
                    index = 0; // no hay suficientes saltos: usamos el único dato disponible
                }
                if (index < values.size()) {
                    return values.get(index);
                }
            }
        }

        return request.getRemoteAddr();
    }
}

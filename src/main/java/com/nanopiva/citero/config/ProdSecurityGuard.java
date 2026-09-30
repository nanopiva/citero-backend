package com.nanopiva.citero.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Falla al arrancar en prod si hay config insegura (cookie sin Secure o consola H2 activa). */
@Component
@RequiredArgsConstructor
public class ProdSecurityGuard {

    private final Environment environment;

    @Value("${citero.auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @PostConstruct
    void verify() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (!cookieSecure) {
            throw new IllegalStateException(
                    "En producción la cookie de refresh debe ir por HTTPS "
                            + "(citero.auth.cookie.secure=true / CITERO_COOKIE_SECURE=true).");
        }
        if (h2ConsoleEnabled) {
            throw new IllegalStateException(
                    "La consola H2 no debe estar habilitada en producción (spring.h2.console.enabled=false).");
        }
    }
}

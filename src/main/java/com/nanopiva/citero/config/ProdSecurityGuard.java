package com.nanopiva.citero.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Guardas de arranque para producción: falla ante config insegura (cookie sin Secure o consola
 * H2 activa) y advierte sobre faltantes de infraestructura que no puede forzar por sí mismo
 * (TLS hacia la base, reutilización de secretos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProdSecurityGuard {

    private final Environment environment;

    @Value("${citero.auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${citero.jwt.secret}")
    private String jwtSecret;

    @Value("${citero.public-link.secret}")
    private String publicLinkSecret;

    @Value("${citero.otp.secret}")
    private String otpSecret;

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
        warnIfDatabaseWithoutTls();
        warnIfKeyReuse("citero.public-link.secret (CITERO_PUBLIC_LINK_SECRET)", publicLinkSecret);
        warnIfKeyReuse("citero.otp.secret (CITERO_OTP_SECRET)", otpSecret);
    }

    private void warnIfDatabaseWithoutTls() {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            return;
        }
        if (!datasourceUrl.toLowerCase(Locale.ROOT).contains("sslmode=")) {
            log.warn("La URL de la base de datos no especifica sslmode. Agregá 'sslmode=require' "
                    + "(o verify-full) para cifrar la conexión a PostgreSQL en producción.");
        }
    }

    private void warnIfKeyReuse(String name, String value) {
        if (value != null && !value.isBlank() && value.equals(jwtSecret)) {
            log.warn("{} reutiliza el secreto del JWT. Configurá un secreto dedicado (separación de claves).", name);
        }
    }
}

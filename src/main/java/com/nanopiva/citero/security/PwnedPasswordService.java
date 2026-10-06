package com.nanopiva.citero.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Chequeo de contraseñas filtradas contra Pwned Passwords (HIBP) usando k-Anonymity: sólo se
 * envía el prefijo de 5 chars del SHA-1 (nunca la contraseña ni el hash completo). El SHA-1 se
 * usa únicamente porque así lo exige la API.
 *
 * <p>Fail-open: ante timeout/error del servicio externo se considera la contraseña aceptable
 * (es un control complementario a la blocklist local, no debe bloquear el alta por una caída
 * de un tercero). Desactivable con {@code citero.security.hibp.enabled=false}.</p>
 */
@Slf4j
@Component
public class PwnedPasswordService {

    private static final String API = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final boolean enabled;
    private final HttpClient httpClient;

    public PwnedPasswordService(@Value("${citero.security.hibp.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** true si la contraseña aparece en filtraciones conocidas. Fail-open ante errores. */
    public boolean isBreached(String password) {
        if (!enabled || password == null || password.isBlank()) {
            return false;
        }
        try {
            String hash = sha1Hex(password);
            String prefix = hash.substring(0, 5);
            String suffix = hash.substring(5);

            HttpRequest request = HttpRequest.newBuilder(URI.create(API + prefix))
                    .header("Add-Padding", "true")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return false;
            }
            for (String line : response.body().split("\n")) {
                int separator = line.indexOf(':');
                if (separator > 0 && line.substring(0, separator).equalsIgnoreCase(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.debug("HIBP no disponible; se omite el chequeo de filtraciones: {}", ex.getMessage());
            return false;
        }
    }

    /** SHA-1 en hex mayúsculas (requerido por la API de HIBP). */
    String sha1Hex(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-1 no disponible.", ex);
        }
    }
}

package com.nanopiva.citero.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final long MAX_BACKOFF_MS = 30_000L;

    private final TemplateEngine templateEngine;
    private final Resend resend;
    private final String fromEmail;
    private final int maxAttempts;
    private final long backoffMs;

    public EmailService(TemplateEngine templateEngine,
                        @Value("${resend.api.key}") String apiKey,
                        @Value("${citero.email.from}") String fromEmail,
                        @Value("${citero.email.retry.max-attempts:3}") int maxAttempts,
                        @Value("${citero.email.retry.backoff-ms:1000}") long backoffMs) {
        this.templateEngine = templateEngine;
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
    }

    /**
     * Envía un email de forma asíncrona utilizando una plantilla Thymeleaf.
     *
     * <p>Ante un fallo de Resend reintenta hasta {@code citero.email.retry.max-attempts}
     * veces con un backoff exponencial. Devuelve un {@link CompletableFuture} que se
     * completa con {@code true} si el email se envió correctamente y {@code false} si
     * falló tras agotar los reintentos. Los llamadores que no necesitan conocer el
     * resultado pueden ignorarlo (comportamiento "fire and forget"); los que sí lo
     * necesitan (p. ej. el envío de recordatorios) pueden encadenar {@code join()}.</p>
     *
     * @param to           Destinatario
     * @param subject      Asunto del email
     * @param templateName Ruta de la plantilla (ej. "emails/otp-email")
     * @param variables    Mapa de variables para renderizar la plantilla
     * @return futuro con {@code true} si el envío fue exitoso, {@code false} en caso contrario
     */
    @Async
    public CompletableFuture<Boolean> sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        // Un error de render es definitivo: no tiene sentido reintentar.
        final String htmlContent;
        try {
            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            htmlContent = templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("Error al renderizar la plantilla '{}' para el email a {}: {}", templateName, to, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }

        ResendException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                CreateEmailOptions params = CreateEmailOptions.builder()
                        .from("Citero <" + fromEmail + ">")
                        .to(to)
                        .subject(subject)
                        .html(htmlContent)
                        .build();

                resend.emails().send(params);

                if (attempt == 1) {
                    log.info("Email enviado exitosamente a {} con asunto '{}'", to, subject);
                } else {
                    log.info("Email enviado exitosamente a {} con asunto '{}' en el intento {}/{}", to, subject, attempt, maxAttempts);
                }
                return CompletableFuture.completedFuture(true);

            } catch (ResendException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    long delay = Math.min(backoffMs * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                    log.warn("Intento {}/{} fallido al enviar email a {} (asunto '{}'): {}. Reintentando en {} ms...",
                            attempt, maxAttempts, to, subject, e.getMessage(), delay);
                    if (!sleep(delay)) {
                        log.error("Envío de email a {} interrumpido durante la espera de reintento.", to);
                        break;
                    }
                }
            } catch (Exception e) {
                // Errores no recuperables (p. ej. al construir la request): no se reintenta.
                log.error("Error inesperado al enviar email a {} (asunto '{}'): {}", to, subject, e.getMessage(), e);
                return CompletableFuture.completedFuture(false);
            }
        }

        log.error("Fallo definitivo al enviar email a {} (asunto '{}') tras {} intento(s). Último error: {}",
                to, subject, maxAttempts, lastError != null ? lastError.getMessage() : "desconocido");
        return CompletableFuture.completedFuture(false);
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

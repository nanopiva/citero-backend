package com.nanopiva.citero.service;

import com.nanopiva.citero.util.Logs;
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
     * Envía un email (plantilla Thymeleaf) de forma asíncrona. Ante un fallo de Resend
     * reintenta con backoff exponencial y resuelve el futuro con {@code true}/{@code false}.
     * Los llamadores que necesitan el resultado pueden encadenar {@code join()}.
     */
    @Async
    public CompletableFuture<Boolean> sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        // Se enmascara el destinatario en los logs para no volcar PII completa.
        String maskedTo = Logs.maskEmail(to);
        // Un error de render es definitivo: no tiene sentido reintentar.
        final String htmlContent;
        try {
            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            htmlContent = templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("Error al renderizar la plantilla '{}' para el email a {}: {}", templateName, maskedTo, e.getMessage(), e);
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
                    log.info("Email enviado exitosamente a {} con asunto '{}'", maskedTo, subject);
                } else {
                    log.info("Email enviado exitosamente a {} con asunto '{}' en el intento {}/{}", maskedTo, subject, attempt, maxAttempts);
                }
                return CompletableFuture.completedFuture(true);

            } catch (ResendException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    long delay = Math.min(backoffMs * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                    log.warn("Intento {}/{} fallido al enviar email a {} (asunto '{}'): {}. Reintentando en {} ms...",
                            attempt, maxAttempts, maskedTo, subject, e.getMessage(), delay);
                    if (!sleep(delay)) {
                        log.error("Envío de email a {} interrumpido durante la espera de reintento.", maskedTo);
                        break;
                    }
                }
            } catch (Exception e) {
                // Errores no recuperables (p. ej. al construir la request): no se reintenta.
                log.error("Error inesperado al enviar email a {} (asunto '{}'): {}", maskedTo, subject, e.getMessage(), e);
                return CompletableFuture.completedFuture(false);
            }
        }

        log.error("Fallo definitivo al enviar email a {} (asunto '{}') tras {} intento(s). Último error: {}",
                maskedTo, subject, maxAttempts, lastError != null ? lastError.getMessage() : "desconocido");
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

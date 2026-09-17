package com.nanopiva.citero.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del {@link EmailService}.
 *
 * <p>Nota de diseño: {@code EmailService} construye su cliente Resend internamente
 * ({@code new Resend(apiKey)} en EmailService.java:35), por lo que <b>no</b> es un bean
 * de Spring y no puede reemplazarse con {@code @MockitoBean}. Para aislar la red se usa
 * {@code mockConstruction} de Mockito, interceptando la creación del cliente Resend.</p>
 */
class EmailServiceTest {

    private TemplateEngine templateEngine;
    private Emails emails;

    @BeforeEach
    void setUp() {
        templateEngine = mock(TemplateEngine.class);
        emails = mock(Emails.class);
    }

    private EmailService newService(int maxAttempts, long backoffMs) {
        return new EmailService(templateEngine, "test-key", "noreply@test.com", maxAttempts, backoffMs);
    }

    private void stubRenderOk() {
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>ok</html>");
    }

    @Test
    void enviaEmailExitosamente() throws Exception {
        stubRenderOk();
        doReturn(new CreateEmailResponse("email-id")).when(emails).send(any());

        try (MockedConstruction<Resend> ignored = mockConstruction(Resend.class,
                (mock, ctx) -> when(mock.emails()).thenReturn(emails))) {

            Boolean sent = newService(3, 0L)
                    .sendEmail("dest@test.com", "Asunto", "emails/otp-email",
                            Map.<String, Object>of("k", "v"))
                    .join();

            assertTrue(sent, "El envío debe reportarse como exitoso");
            verify(emails, times(1)).send(any());
        }
    }

    @Test
    void reintentaYTerminaExitosamente() throws Exception {
        stubRenderOk();
        doThrow(new ResendException("primer fallo"))
                .doReturn(new CreateEmailResponse("email-id"))
                .when(emails).send(any());

        try (MockedConstruction<Resend> ignored = mockConstruction(Resend.class,
                (mock, ctx) -> when(mock.emails()).thenReturn(emails))) {

            Boolean sent = newService(3, 0L)
                    .sendEmail("dest@test.com", "Asunto", "emails/otp-email", Map.of())
                    .join();

            assertTrue(sent, "Debe terminar exitoso tras el reintento");
            verify(emails, times(2)).send(any());
        }
    }

    @Test
    void agotaReintentosYDevuelveFalse() throws Exception {
        stubRenderOk();
        doThrow(new ResendException("fallo permanente")).when(emails).send(any());

        try (MockedConstruction<Resend> ignored = mockConstruction(Resend.class,
                (mock, ctx) -> when(mock.emails()).thenReturn(emails))) {

            Boolean sent = newService(2, 0L)
                    .sendEmail("dest@test.com", "Asunto", "emails/otp-email", Map.of())
                    .join();

            assertFalse(sent, "Tras agotar los intentos debe devolver false");
            verify(emails, times(2)).send(any());
        }
    }

    @Test
    void maxAttemptsCeroSeNormalizaAUnIntento() throws Exception {
        stubRenderOk();
        doThrow(new ResendException("fallo")).when(emails).send(any());

        try (MockedConstruction<Resend> ignored = mockConstruction(Resend.class,
                (mock, ctx) -> when(mock.emails()).thenReturn(emails))) {

            Boolean sent = newService(0, 0L)
                    .sendEmail("dest@test.com", "Asunto", "emails/otp-email", Map.of())
                    .join();

            assertFalse(sent, "Con maxAttempts<=0 igual se hace un intento");
            verify(emails, times(1)).send(any());
        }
    }

    @Test
    void errorDePlantillaNoReintentaNiEnvia() throws Exception {
        when(templateEngine.process(anyString(), any(IContext.class)))
                .thenThrow(new RuntimeException("plantilla inexistente"));

        try (MockedConstruction<Resend> ignored = mockConstruction(Resend.class,
                (mock, ctx) -> when(mock.emails()).thenReturn(emails))) {

            Boolean sent = newService(3, 0L)
                    .sendEmail("dest@test.com", "Asunto", "emails/no-existe", Map.of())
                    .join();

            assertFalse(sent, "Un error de render no debe reintentarse");
            verify(emails, never()).send(any());
        }
    }
}

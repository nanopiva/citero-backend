package com.nanopiva.citero.controller;

import com.nanopiva.citero.service.EmailService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El rate limiting está deshabilitado en el perfil de test para no interferir con el
 * resto de la suite; este test lo habilita explícitamente en su propio contexto.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "citero.rate-limit.enabled=true")
class RateLimitFilterTest extends IntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"nadie@test.com\",\"password\":\"incorrecta\"}";

    @Autowired private MockMvc mockMvc;

    // Evita llamadas reales a Resend al pedir OTP.
    @MockitoBean private EmailService emailService;

    @Test
    void loginSeBloqueaAlSuperarElLimitePorIp() throws Exception {
        String ip = "198.51.100.10";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void envioDeOtpSeBloqueaAlSuperarElLimitePorIp() throws Exception {
        String ip = "198.51.100.11";
        String body = "{\"target\":\"ratelimit@test.com\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/otp/send")
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        mockMvc.perform(post("/api/otp/send")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }
}

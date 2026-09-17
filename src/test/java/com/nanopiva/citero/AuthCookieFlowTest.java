package com.nanopiva.citero;

import com.nanopiva.citero.service.EmailService;
import com.nanopiva.citero.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthCookieFlowTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;

    // Evita llamadas reales a Resend al registrar (dispara un OTP de verificación).
    @MockitoBean private EmailService emailService;

    private String cookieValue(String setCookieHeader) {
        String first = setCookieHeader.split(";")[0];
        return first.substring(first.indexOf('=') + 1);
    }

    @Test
    void registerSetsHttpOnlyRefreshCookieAndRefreshWorks() throws Exception {
        String email = "cookie-" + System.nanoTime() + "@test.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";

        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String setCookie = register.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("citero_refresh="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Path=/api/auth"));
        assertTrue(setCookie.contains("SameSite=Lax"));

        String refreshCookie = cookieValue(setCookie);

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("citero_refresh", refreshCookie)))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(refresh.getResponse().getContentAsString().contains("\"token\""));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("citero_refresh", refreshCookie)))
                .andExpect(status().isNoContent())
                .andReturn();

        String clearCookie = logout.getResponse().getHeader("Set-Cookie");
        assertNotNull(clearCookie);
        assertTrue(clearCookie.contains("Max-Age=0"));
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }
}

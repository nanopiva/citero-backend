package com.nanopiva.citero.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;

    // Evita llamadas reales a Resend al registrar (dispara un OTP de verificación).
    @MockitoBean private EmailService emailService;

    private String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String cookieValue(String setCookieHeader) {
        String first = setCookieHeader.split(";")[0];
        return first.substring(first.indexOf('=') + 1);
    }

    @Test
    void registerDevuelve201ConTokenYCookie() throws Exception {
        String email = uniqueEmail("register");

        MvcResult result = register(email);

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie, "El registro debe setear la cookie de refresh");
        assertTrue(setCookie.contains("citero_refresh="), "Debe usar el nombre de cookie configurado");
        assertTrue(setCookie.contains("HttpOnly"), "La cookie debe ser HttpOnly");
        assertTrue(result.getResponse().getContentAsString().contains("\"token\""),
                "La respuesta debe incluir el access token");
    }

    @Test
    void loginConCredencialesValidasDevuelveToken() throws Exception {
        String email = uniqueEmail("login");
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve400() throws Exception {
        String email = uniqueEmail("badlogin");
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshConCookieValidaDevuelveNuevoToken() throws Exception {
        String email = uniqueEmail("refresh");
        MvcResult registered = register(email);
        String rawToken = cookieValue(registered.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("citero_refresh", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void refreshSinCookieDevuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDevuelve204YBorraLaCookie() throws Exception {
        String email = uniqueEmail("logout");
        MvcResult registered = register(email);
        String rawToken = cookieValue(registered.getResponse().getHeader("Set-Cookie"));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("citero_refresh", rawToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String clearCookie = logout.getResponse().getHeader("Set-Cookie");
        assertNotNull(clearCookie, "El logout debe emitir la cookie de limpieza");
        assertTrue(clearCookie.contains("Max-Age=0"), "La cookie debe expirar");
    }
}

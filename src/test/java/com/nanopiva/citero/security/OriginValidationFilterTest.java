package com.nanopiva.citero.security;

import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La allowlist de CORS en el perfil de test es {@code http://localhost:3000}
 * (ver {@code application-test.properties}).
 */
@AutoConfigureMockMvc
class OriginValidationFilterTest extends IntegrationTest {

    private static final String ALLOWED = "http://localhost:3000";
    private static final String FOREIGN = "http://evil.example";

    @Autowired private MockMvc mockMvc;

    @Test
    void sinOriginNiRefererNoSeBloquea() throws Exception {
        // Sin cookie, responde 401 (pero no 403): no hay navegador que pueda hacer CSRF.
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void originAjenoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("Origin", FOREIGN))
                .andExpect(status().isForbidden());
    }

    @Test
    void originPermitidoNoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("Origin", ALLOWED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refererAjenoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("Referer", FOREIGN + "/pagina"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refererPermitidoNoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("Referer", ALLOWED + "/pagina"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elFiltroSoloAplicaAAuth() throws Exception {
        // /api/appointments no es un endpoint con cookie: pasa el filtro y falla por validación.
        mockMvc.perform(post("/api/appointments")
                        .header("Origin", ALLOWED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

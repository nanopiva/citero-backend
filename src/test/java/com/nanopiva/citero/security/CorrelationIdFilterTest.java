package com.nanopiva.citero.security;

import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CorrelationIdFilterTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void propagaElIdEntrante() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @Test
    void generaUnIdCuandoNoVieneOEsInvalido() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Request-Id"));

        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "bad id!!"))
                .andExpect(header().string("X-Request-Id", not("bad id!!")));
    }

    @Test
    void lasRespuestasDeErrorIncluyenElRequestId() throws Exception {
        mockMvc.perform(get("/api/users/me").header("X-Request-Id", "req-err-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "req-err-1"))
                .andExpect(jsonPath("$.requestId").value("req-err-1"));
    }
}

package com.nanopiva.citero.controller;

import com.nanopiva.citero.service.EmailService;
import com.nanopiva.citero.support.IntegrationTest;
import com.nanopiva.citero.support.OtpTestCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OtpControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;

    // Evita llamadas reales a Resend al generar OTPs.
    @MockitoBean private EmailService emailService;

    private String uniqueTarget(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    @Test
    void sendGeneraOtpYVerifyLoAcepta() throws Exception {
        String target = uniqueTarget("otp-send");

        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"" + target + "\"}"))
                .andExpect(status().isNoContent());

        String code = OtpTestCodes.latestFor(emailService, target);
        assertNotNull(code, "Debe persistirse un OTP para el target");

        mockMvc.perform(post("/api/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"" + target + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void verifyConCodigoIncorrectoDevuelve400() throws Exception {
        String target = uniqueTarget("otp-wrong");

        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"" + target + "\"}"))
                .andExpect(status().isNoContent());

        String realCode = OtpTestCodes.latestFor(emailService, target);
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";

        mockMvc.perform(post("/api/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"" + target + "\",\"code\":\"" + wrongCode + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendConTargetVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}

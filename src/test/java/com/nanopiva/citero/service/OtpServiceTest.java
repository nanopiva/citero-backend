package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.OtpToken;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class OtpServiceTest extends IntegrationTest {

    @Autowired private OtpService otpService;
    @Autowired private OtpTokenRepository otpTokenRepository;

    // Evita llamadas reales a Resend al generar OTPs.
    @MockitoBean private EmailService emailService;

    private String uniqueTarget(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    private OtpToken latestToken(String target) {
        return otpTokenRepository.findAll().stream()
                .filter(token -> target.equals(token.getTarget()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No se generó ningún OTP para " + target));
    }

    @Test
    void generarYVerificarOtpEsExitoso() {
        String target = uniqueTarget("otp-ok");

        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);

        OtpToken token = latestToken(target);
        assertNotNull(token.getCode(), "Debe generarse un código");
        assertEquals(6, token.getCode().length(), "El código debe tener 6 dígitos");

        otpService.verifyOtp(target, token.getCode(), OtpService.PURPOSE_GUEST_VERIFICATION);

        OtpToken used = otpTokenRepository.findById(token.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(used.getIsUsed()), "El OTP debe marcarse como usado");
    }

    @Test
    void codigoIncorrectoEsRechazado() {
        String target = uniqueTarget("otp-wrong");
        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);
        String realCode = latestToken(target).getCode();
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, wrongCode, OtpService.PURPOSE_GUEST_VERIFICATION));
    }

    @Test
    void codigoExpiradoEsRechazado() {
        String target = uniqueTarget("otp-expired");
        otpTokenRepository.save(OtpToken.builder()
                .target(target)
                .code("123456")
                .purpose(OtpService.PURPOSE_GUEST_VERIFICATION)
                .expirationTime(LocalDateTime.now().minusMinutes(1))
                .isUsed(false)
                .build());

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, "123456", OtpService.PURPOSE_GUEST_VERIFICATION));
    }

    @Test
    void otpDeUnPropositoNoSirveParaOtro() {
        String target = uniqueTarget("otp-purpose");
        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);
        String code = latestToken(target).getCode();

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, code, OtpService.PURPOSE_PASSWORD_RESET));
    }

    @Test
    void demasiadosIntentosInvalidanElOtp() {
        String target = uniqueTarget("otp-bruteforce");
        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);
        String realCode = latestToken(target).getCode();
        String wrongCode = "000000".equals(realCode) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThrows(BadRequestException.class,
                    () -> otpService.verifyOtp(target, wrongCode, OtpService.PURPOSE_GUEST_VERIFICATION));
        }

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, realCode, OtpService.PURPOSE_GUEST_VERIFICATION),
                "Tras superar el máximo de intentos, incluso el código correcto queda invalidado");
    }

    @Test
    void emitirUnNuevoOtpInvalidaElAnterior() {
        String target = uniqueTarget("otp-reissue");
        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);
        String firstCode = latestToken(target).getCode();

        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, firstCode, OtpService.PURPOSE_GUEST_VERIFICATION),
                "Solo el último OTP emitido debe ser válido");
    }

    @Test
    void otpYaUsadoNoPuedeReutilizarse() {
        String target = uniqueTarget("otp-reuse");
        otpService.generateAndSendOtp(target, OtpService.PURPOSE_GUEST_VERIFICATION);
        String code = latestToken(target).getCode();

        otpService.verifyOtp(target, code, OtpService.PURPOSE_GUEST_VERIFICATION);

        assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp(target, code, OtpService.PURPOSE_GUEST_VERIFICATION));
    }
}

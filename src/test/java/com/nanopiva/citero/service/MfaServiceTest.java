package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.MfaSetupResponseDto;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.TotpService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class MfaServiceTest extends IntegrationTest {

    @Autowired private MfaService mfaService;
    @Autowired private TotpService totpService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .email("mfa-" + tag + "-" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("secret123"))
                .emailVerified(true)
                .build());
    }

    @Test
    void enrolaActivaYVerificaConTotp() {
        User user = newUser("enroll");

        MfaSetupResponseDto setup = mfaService.beginSetup(user.getId());
        assertTrue(setup.getOtpauthUri().startsWith("otpauth://totp/"));
        assertTrue(setup.getSecret().matches("[A-Z2-7]+"));

        List<String> recovery = mfaService.enable(user.getId(), totpService.currentCode(setup.getSecret()))
                .getRecoveryCodes();
        assertEquals(10, recovery.size());
        assertTrue(mfaService.status(user.getId()).isEnabled());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(totpService.verify(reloaded.getTotpSecret(), totpService.currentCode(reloaded.getTotpSecret())));
    }

    @Test
    void activarConCodigoIncorrectoEsRechazado() {
        User user = newUser("bad-code");
        String secret = mfaService.beginSetup(user.getId()).getSecret();
        String invalid = "000000".equals(totpService.currentCode(secret)) ? "111111" : "000000";

        assertThrows(BadRequestException.class, () -> mfaService.enable(user.getId(), invalid));
        assertFalse(mfaService.status(user.getId()).isEnabled());
    }

    @Test
    void elCodigoDeRecuperacionSirveUnaSolaVez() {
        User user = newUser("recovery");
        String secret = mfaService.beginSetup(user.getId()).getSecret();
        List<String> recovery = mfaService.enable(user.getId(), totpService.currentCode(secret)).getRecoveryCodes();

        String recoveryCode = recovery.get(0);
        User reloaded = userRepository.findById(user.getId()).orElseThrow();

        assertTrue(mfaService.verifyLoginCode(reloaded, recoveryCode), "El código de recuperación debe ser válido");
        User afterUse = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(mfaService.verifyLoginCode(afterUse, recoveryCode), "No debe reutilizarse");
    }

    @Test
    void desactivarExigePasswordYCodigoValido() {
        User user = newUser("disable");
        String secret = mfaService.beginSetup(user.getId()).getSecret();
        mfaService.enable(user.getId(), totpService.currentCode(secret));

        assertThrows(BadRequestException.class,
                () -> mfaService.disable(user.getId(), "incorrecta", totpService.currentCode(secret)),
                "Password incorrecta debe rechazarse");

        mfaService.disable(user.getId(), "secret123", totpService.currentCode(secret));
        assertFalse(mfaService.status(user.getId()).isEnabled());
    }
}

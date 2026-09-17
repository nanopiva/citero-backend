package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.entity.OtpToken;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AuthServiceTest extends IntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private OtpTokenRepository otpTokenRepository;

    // Evita llamadas reales a Resend al generar OTPs.
    @MockitoBean private EmailService emailService;

    private String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    private User createUser(String email, String rawPassword) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .build());
    }

    private String latestOtpCode(String target) {
        return otpTokenRepository.findAll().stream()
                .filter(token -> target.equals(token.getTarget()))
                .map(OtpToken::getCode)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se generó ningún OTP para " + target));
    }

    @Test
    void loginConCredencialesValidasDevuelveTokensYUsuario() {
        String email = uniqueEmail("login-ok");
        createUser(email, "secret123");

        AuthService.AuthResult result = authService.login(
                LoginRequestDto.builder().email(email).password("secret123").build(),
                "JUnit", "127.0.0.1");

        assertNotNull(result.accessToken(), "Debe devolverse un access token");
        assertFalse(result.accessToken().isBlank(), "El access token no debe estar vacío");
        assertNotNull(result.refreshToken(), "Debe devolverse un refresh token");
        assertFalse(result.refreshToken().isBlank(), "El refresh token no debe estar vacío");
        assertEquals(email, result.user().getEmail(), "El usuario devuelto debe coincidir");
    }

    @Test
    void loginConContrasenaIncorrectaLanzaBadRequest() {
        String email = uniqueEmail("login-bad");
        createUser(email, "secret123");

        assertThrows(BadRequestException.class, () -> authService.login(
                LoginRequestDto.builder().email(email).password("incorrecta").build(),
                "JUnit", "127.0.0.1"));
    }

    @Test
    void loginConEmailInexistenteLanzaBadRequest() {
        assertThrows(BadRequestException.class, () -> authService.login(
                LoginRequestDto.builder().email(uniqueEmail("login-unknown")).password("secret123").build(),
                "JUnit", "127.0.0.1"));
    }

    @Test
    void refreshConSesionValidaRotaElToken() {
        User user = createUser(uniqueEmail("refresh-ok"), "secret123");
        RefreshTokenService.RefreshTokenPair first = refreshTokenService.issue(user, "JUnit", "127.0.0.1");

        AuthService.AuthResult result = authService.refresh(first.rawToken(), "JUnit", "127.0.0.1");

        assertNotNull(result.accessToken(), "Debe emitirse un nuevo access token");
        assertEquals(user.getEmail(), result.user().getEmail(), "El usuario debe coincidir");
        assertNotEquals(first.rawToken(), result.refreshToken(), "El refresh token debe rotar");
    }

    @Test
    void refreshSinSesionValidaLanzaUnauthorized() {
        assertThrows(UnauthorizedException.class,
                () -> authService.refresh("token-inexistente", "JUnit", "127.0.0.1"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void logoutRevocaLaSesion() {
        User user = createUser(uniqueEmail("logout"), "secret123");
        RefreshTokenService.RefreshTokenPair pair = refreshTokenService.issue(user, "JUnit", "127.0.0.1");

        authService.logout(pair.rawToken());

        assertTrue(refreshTokenService.rotate(pair.rawToken(), "JUnit", "127.0.0.1").isEmpty(),
                "Tras el logout el refresh token ya no debe poder rotarse");
    }

    @Test
    void forgotPasswordEsAntiEnumeracion() {
        String existing = uniqueEmail("forgot-existing");
        createUser(existing, "secret123");

        assertDoesNotThrow(() -> authService.requestPasswordReset(existing));
        assertTrue(otpTokenRepository.findAll().stream().anyMatch(t -> existing.equals(t.getTarget())),
                "Para un email registrado debe generarse un OTP");

        String unknown = uniqueEmail("forgot-unknown");
        assertDoesNotThrow(() -> authService.requestPasswordReset(unknown),
                "Un email inexistente no debe revelar su ausencia con una excepción");
        assertTrue(otpTokenRepository.findAll().stream().noneMatch(t -> unknown.equals(t.getTarget())),
                "No debe generarse OTP para un email inexistente");
    }

    @Test
    void resetPasswordCambiaLaContrasenaConUnOtpValido() {
        String email = uniqueEmail("reset");
        User user = createUser(email, "vieja123");
        authService.requestPasswordReset(email);
        String code = latestOtpCode(email);

        authService.resetPassword(email, code, "nueva123");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("nueva123", reloaded.getPassword()),
                "La contraseña debe quedar actualizada");

        assertThrows(BadRequestException.class, () -> authService.resetPassword(email, code, "otra123"),
                "El OTP es de un solo uso");
    }
}

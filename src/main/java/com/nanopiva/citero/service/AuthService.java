package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.TooManyRequestsException;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.CommonPasswordCheck;
import com.nanopiva.citero.security.LoginAttemptService;
import com.nanopiva.citero.security.PwnedPasswordService;
import com.nanopiva.citero.security.RateLimitStore;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.util.Emails;
import com.nanopiva.citero.util.Logs;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // Cap por email (además del cap por IP del filtro): frena fuerza bruta dirigida
    // a una cuenta aunque el atacante rote IPs.
    private static final int MAX_LOGIN_PER_EMAIL = 10;
    private static final Duration LOGIN_EMAIL_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final RateLimitStore rateLimitStore;
    private final LoginAttemptService loginAttemptService;
    private final MfaService mfaService;
    private final PwnedPasswordService pwnedPasswordService;

    /**
     * Resultado interno de una autenticación: access token (para el body), usuario y
     * refresh token en claro (para setear la cookie HttpOnly). El refresh nunca se
     * expone en el body de la respuesta.
     */
    public record AuthResult(String accessToken, UserResponseDto user, String refreshToken) {
    }

    /**
     * Resultado del login: o bien tokens, o bien un desafío MFA pendiente (la cuenta tiene TOTP
     * activo y falta el segundo factor).
     */
    public record LoginResult(String accessToken, UserResponseDto user, String refreshToken,
                              boolean mfaRequired, String mfaToken) {
        static LoginResult tokens(AuthResult result) {
            return new LoginResult(result.accessToken(), result.user(), result.refreshToken(), false, null);
        }

        static LoginResult mfa(String mfaToken) {
            return new LoginResult(null, null, null, true, mfaToken);
        }
    }

    @Transactional
    public LoginResult login(LoginRequestDto requestDto, String userAgent, String ipAddress) {
        String normalizedEmail = Emails.normalize(requestDto.getEmail());

        // Bloqueo temporal por cuenta (anti fuerza bruta), además del cap por IP/email.
        if (loginAttemptService.isLocked(normalizedEmail)) {
            log.warn("security_event=auth_lockout user={} ip={} retry_after_s={}",
                    Logs.maskEmail(normalizedEmail), ipAddress,
                    loginAttemptService.retryAfterSeconds(normalizedEmail));
            throw new TooManyRequestsException(
                    "Demasiados intentos fallidos. La cuenta está bloqueada temporalmente; probá de nuevo en unos minutos.");
        }

        if (!rateLimitStore.tryConsume(
                "login-email:" + normalizedEmail, MAX_LOGIN_PER_EMAIL, LOGIN_EMAIL_WINDOW)) {
            throw new TooManyRequestsException(
                    "Demasiados intentos para esta cuenta. Esperá unos minutos e intentá de nuevo.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            requestDto.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            // Auditoría de intentos fallidos (sin contraseña, con email enmascarado).
            loginAttemptService.recordFailure(normalizedEmail);
            log.warn("security_event=auth_login_fail user={} ip={}",
                    Logs.maskEmail(normalizedEmail), ipAddress);
            throw new UnauthorizedException("Email o contraseña incorrectos");
        }

        // Autenticación correcta: limpia el contador de fallos de la cuenta.
        loginAttemptService.reset(normalizedEmail);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UnauthorizedException("Email o contraseña incorrectos"));

        // Cuenta sin email verificado: no puede ingresar (evita reclamos por email ajeno).
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new UnauthorizedException("Tenés que verificar tu email antes de ingresar.");
        }

        // Reconcilia invitaciones de staff pendientes para este email: cubre a quien se
        // registró por su cuenta y a cuentas que ya existían antes de ser invitadas.
        userService.linkPendingStaff(user);

        // Con MFA activo no se emiten tokens: se devuelve un desafío para el segundo factor.
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            return LoginResult.mfa(jwtService.generateMfaToken(user.getId()));
        }

        String accessToken = jwtService.generateTokenFromUserDetails(userDetails);
        RefreshTokenService.RefreshTokenPair refresh = refreshTokenService.issue(user, userAgent, ipAddress);

        return LoginResult.tokens(new AuthResult(accessToken, mapToUserResponseDto(user), refresh.rawToken()));
    }

    /**
     * Segundo paso del login: valida el código TOTP/de recuperación contra el desafío MFA y,
     * si es correcto, emite los tokens.
     */
    @Transactional
    public AuthResult loginMfa(String mfaToken, String code, String userAgent, String ipAddress) {
        Long userId;
        try {
            userId = jwtService.parseMfaToken(mfaToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Desafío MFA inválido o expirado.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Desafío MFA inválido o expirado."));

        if (!mfaService.verifyLoginCode(user, code)) {
            log.warn("security_event=auth_mfa_fail user={}", Logs.maskEmail(user.getEmail()));
            throw new UnauthorizedException("Código de verificación incorrecto.");
        }

        loginAttemptService.reset(user.getEmail());
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String accessToken = jwtService.generateTokenFromUserDetails(userDetails);
        RefreshTokenService.RefreshTokenPair refresh = refreshTokenService.issue(user, userAgent, ipAddress);
        return new AuthResult(accessToken, mapToUserResponseDto(user), refresh.rawToken());
    }

    /**
     * Valida la política de contraseña y, si está OK, envía el OTP de verificación de email
     * (sólo lo manda si el email es registrable). Validar la contraseña acá evita avanzar al
     * paso del OTP y consumirlo con una contraseña rechazada.
     */
    @Transactional
    public void requestRegistrationOtp(String email, String password) {
        if (CommonPasswordCheck.isCommon(password)) {
            throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
        }
        if (pwnedPasswordService.isBreached(password)) {
            throw new BadRequestException(
                    "Esa contraseña apareció en una filtración de datos conocida. Elegí otra.");
        }

        boolean registrable = userRepository.findByEmail(Emails.normalize(email))
                .map(user -> Boolean.TRUE.equals(user.getIsGuest()))
                .orElse(true);
        try {
            otpService.generateAndSendOtp(email, OtpService.PURPOSE_EMAIL_VERIFICATION, registrable);
        } catch (BadRequestException ex) {
            log.debug("OTP de registro no enviado para {}: {}", Logs.maskEmail(email), ex.getMessage());
        }
    }

    /** Registra una cuenta validando el OTP de email; queda verificada y logueada. */
    @Transactional
    public AuthResult register(RegisterRequestDto requestDto, String userAgent, String ipAddress) {
        // Valida la contraseña ANTES de consumir el OTP (para no gastarlo con una inválida).
        if (CommonPasswordCheck.isCommon(requestDto.getPassword())) {
            throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
        }
        if (pwnedPasswordService.isBreached(requestDto.getPassword())) {
            throw new BadRequestException(
                    "Esa contraseña apareció en una filtración de datos conocida. Elegí otra.");
        }

        otpService.verifyOtp(
                requestDto.getEmail(), requestDto.getOtpCode(), OtpService.PURPOSE_EMAIL_VERIFICATION);

        UserResponseDto created = userService.register(requestDto);
        User user = userRepository.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario recién creado no encontrado."));

        // Cuenta recién registrada/verificada: no arrastra un bloqueo previo.
        loginAttemptService.reset(Emails.normalize(requestDto.getEmail()));

        String accessToken = jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
        RefreshTokenService.RefreshTokenPair refresh = refreshTokenService.issue(user, userAgent, ipAddress);
        return new AuthResult(accessToken, mapToUserResponseDto(user), refresh.rawToken());
    }

    /**
     * Rota el refresh token y emite un nuevo access token.
     *
     * <p>No es transaccional a propósito: {@link RefreshTokenService#rotate} maneja su
     * propia transacción y debe poder confirmar la revocación por reuso antes de que
     * lancemos el 401.</p>
     */
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshTokenService.RefreshTokenPair pair = refreshTokenService
                .rotate(rawRefreshToken, userAgent, ipAddress)
                .orElseThrow(() -> new UnauthorizedException("Sesión inválida o expirada. Iniciá sesión nuevamente."));
        User user = pair.user();
        String accessToken = jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
        return new AuthResult(accessToken, mapToUserResponseDto(user), pair.rawToken());
    }

    /**
     * Revoca la sesión asociada al refresh token.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /** Solicita el reseteo; envía el OTP sólo si la cuenta existe (respuesta uniforme). */
    @Transactional
    public void requestPasswordReset(String email) {
        boolean exists = userRepository.findByEmail(Emails.normalize(email)).isPresent();
        try {
            // Token siempre (trabajo uniforme); se envía sólo si la cuenta existe.
            otpService.generateAndSendOtp(email, OtpService.PURPOSE_PASSWORD_RESET, exists);
        } catch (BadRequestException ex) {
            log.debug("OTP de recuperación no enviado para {}: {}", Logs.maskEmail(email), ex.getMessage());
        }
    }

    /** Resetea la contraseña validando el OTP; la cuenta queda verificada. */
    @Transactional
    public void resetPassword(String email, String otpCode, String newPassword) {
        String normalizedEmail = Emails.normalize(email);
        otpService.verifyOtp(normalizedEmail, otpCode, OtpService.PURPOSE_PASSWORD_RESET);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado."));

        if (CommonPasswordCheck.isCommon(newPassword)) {
            throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
        }
        if (pwnedPasswordService.isBreached(newPassword)) {
            throw new BadRequestException(
                    "Esa contraseña apareció en una filtración de datos conocida. Elegí otra.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // El OTP validó el email: la cuenta queda verificada.
        user.setEmailVerified(true);
        userRepository.save(user);

        // El dueño legítimo recupera el acceso aunque estuviera bloqueado por intentos fallidos.
        loginAttemptService.reset(normalizedEmail);

        // Cierra las sesiones existentes: si el token estaba comprometido, deja de servir.
        refreshTokenService.revokeAllForUser(user);
    }

    private UserResponseDto mapToUserResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .name(user.getName())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

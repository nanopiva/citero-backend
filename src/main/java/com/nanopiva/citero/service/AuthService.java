package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.CommonPasswordCheck;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.util.Emails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    /**
     * Resultado interno de una autenticación: access token (para el body), usuario y
     * refresh token en claro (para setear la cookie HttpOnly). El refresh nunca se
     * expone en el body de la respuesta.
     */
    public record AuthResult(String accessToken, UserResponseDto user, String refreshToken) {
    }

    @Transactional
    public AuthResult login(LoginRequestDto requestDto, String userAgent, String ipAddress) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            Emails.normalize(requestDto.getEmail()),
                            requestDto.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Email o contraseña incorrectos");
        }

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

        String accessToken = jwtService.generateTokenFromUserDetails(userDetails);
        RefreshTokenService.RefreshTokenPair refresh = refreshTokenService.issue(user, userAgent, ipAddress);

        return new AuthResult(accessToken, mapToUserResponseDto(user), refresh.rawToken());
    }

    /** Envía el OTP de verificación de email; sólo lo manda si el email es registrable. */
    @Transactional
    public void requestRegistrationOtp(String email) {
        boolean registrable = userRepository.findByEmail(Emails.normalize(email))
                .map(user -> Boolean.TRUE.equals(user.getIsGuest()))
                .orElse(true);
        try {
            otpService.generateAndSendOtp(email, OtpService.PURPOSE_EMAIL_VERIFICATION, registrable);
        } catch (BadRequestException ex) {
            log.debug("OTP de registro no enviado para {}: {}", email, ex.getMessage());
        }
    }

    /** Registra una cuenta validando el OTP de email; queda verificada y logueada. */
    @Transactional
    public AuthResult register(RegisterRequestDto requestDto, String userAgent, String ipAddress) {
        // Valida la contraseña ANTES de consumir el OTP (para no gastarlo con una inválida).
        if (CommonPasswordCheck.isCommon(requestDto.getPassword())) {
            throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
        }

        otpService.verifyOtp(
                requestDto.getEmail(), requestDto.getOtpCode(), OtpService.PURPOSE_EMAIL_VERIFICATION);

        UserResponseDto created = userService.register(requestDto);
        User user = userRepository.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario recién creado no encontrado."));

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
            log.debug("OTP de recuperación no enviado para {}: {}", email, ex.getMessage());
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

        user.setPassword(passwordEncoder.encode(newPassword));
        // El OTP validó el email: la cuenta queda verificada.
        user.setEmailVerified(true);
        userRepository.save(user);

        // Cierra las sesiones existentes: si el token estaba comprometido, deja de servir.
        refreshTokenService.revokeAllForUser(user);
    }

    private UserResponseDto mapToUserResponseDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getEmailVerified(),
                user.getCreatedAt()
        );
    }
}

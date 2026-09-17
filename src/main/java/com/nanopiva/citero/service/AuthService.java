package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                            requestDto.getEmail(),
                            requestDto.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new BadRequestException("Email o contraseña incorrectos");
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new BadRequestException("Email o contraseña incorrectos"));

        // Reconcilia invitaciones de staff pendientes para este email: cubre a quien se
        // registró por su cuenta y a cuentas que ya existían antes de ser invitadas.
        userService.linkPendingStaff(user);

        String accessToken = jwtService.generateTokenFromUserDetails(userDetails);
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

    /**
     * Solicita el reseteo de contraseña.
     * Si el email existe, genera y envía un OTP con propósito PASSWORD_RESET.
     * Si no existe, no hace nada (por seguridad, para no revelar si el email está registrado).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            otpService.generateAndSendOtp(user.getEmail(), OtpService.PURPOSE_PASSWORD_RESET);
        });
    }

    /**
     * Resetea la contraseña usando el OTP enviado.
     * Valida el OTP, busca el usuario, hashea la nueva contraseña y la guarda.
     */
    @Transactional
    public void resetPassword(String email, String otpCode, String newPassword) {
        otpService.verifyOtp(email, otpCode, OtpService.PURPOSE_PASSWORD_RESET);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
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

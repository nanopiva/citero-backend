package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.user.AuthResponseDto;
import com.nanopiva.citero.dto.user.ForgotPasswordRequestDto;
import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.LoginResponseDto;
import com.nanopiva.citero.dto.user.MfaVerifyRequestDto;
import com.nanopiva.citero.dto.user.RegisterOtpRequestDto;
import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.ResetPasswordRequestDto;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.security.ClientIpResolver;
import com.nanopiva.citero.service.AuthCookieService;
import com.nanopiva.citero.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(requestDto, userAgent(request), clientIp(request));
        authCookieService.setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(result));
    }

    /**
     * Envía el código (OTP) para verificar el email durante el registro.
     */
    @PostMapping("/register/request-otp")
    public ResponseEntity<Void> requestRegisterOtp(@Valid @RequestBody RegisterOtpRequestDto requestDto) {
        authService.requestRegistrationOtp(requestDto.getEmail(), requestDto.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(requestDto, userAgent(request), clientIp(request));
        if (result.mfaRequired()) {
            // Sin tokens: el cliente debe completar el segundo factor en /login/mfa.
            return ResponseEntity.ok(LoginResponseDto.builder()
                    .mfaRequired(true)
                    .mfaToken(result.mfaToken())
                    .build());
        }
        authCookieService.setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(LoginResponseDto.builder()
                .token(result.accessToken())
                .tokenType("Bearer")
                .user(result.user())
                .build());
    }

    /**
     * Segundo paso del login (MFA): valida el código contra el desafío y emite los tokens.
     */
    @PostMapping("/login/mfa")
    public ResponseEntity<AuthResponseDto> loginMfa(@Valid @RequestBody MfaVerifyRequestDto requestDto,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        AuthService.AuthResult result = authService.loginMfa(
                requestDto.getMfaToken(), requestDto.getCode(), userAgent(request), clientIp(request));
        authCookieService.setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(toResponseDto(result));
    }

    /**
     * Rota el refresh token (cookie HttpOnly) y devuelve un nuevo access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(HttpServletRequest request,
                                                   HttpServletResponse response) {
        String rawRefreshToken = readRefreshCookie(request);
        if (rawRefreshToken == null) {
            throw new UnauthorizedException("No hay una sesión activa.");
        }
        AuthService.AuthResult result = authService.refresh(rawRefreshToken, userAgent(request), clientIp(request));
        authCookieService.setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(toResponseDto(result));
    }

    /**
     * Revoca la sesión y borra la cookie del refresh token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        authService.logout(readRefreshCookie(request));
        authCookieService.clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto requestDto) {
        authService.requestPasswordReset(requestDto.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto requestDto) {
        authService.resetPassword(requestDto.getEmail(), requestDto.getOtpCode(), requestDto.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthResponseDto toResponseDto(AuthService.AuthResult result) {
        return new AuthResponseDto(result.accessToken(), "Bearer", result.user());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = authCookieService.getCookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String clientIp(HttpServletRequest request) {
        // Resuelve la IP real desde X-Forwarded-For tomando el valor que dejó el proxy
        // de confianza (no el left-most, que puede venir falsificado por el cliente).
        return clientIpResolver.resolve(request);
    }
}

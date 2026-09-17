package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.user.AuthResponseDto;
import com.nanopiva.citero.dto.user.ForgotPasswordRequestDto;
import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.ResetPasswordRequestDto;
import com.nanopiva.citero.exception.UnauthorizedException;
import com.nanopiva.citero.service.AuthCookieService;
import com.nanopiva.citero.service.AuthService;
import com.nanopiva.citero.service.UserService;
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

    private final UserService userService;
    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        userService.register(requestDto);
        AuthService.AuthResult result = authService.login(
                new LoginRequestDto(requestDto.getEmail(), requestDto.getPassword()),
                userAgent(request),
                clientIp(request));
        authCookieService.setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(requestDto, userAgent(request), clientIp(request));
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
        // Con server.forward-headers-strategy=framework, getRemoteAddr() ya resuelve
        // X-Forwarded-For cuando el proxy es de confianza (evita confiar en el header crudo).
        return request.getRemoteAddr();
    }
}

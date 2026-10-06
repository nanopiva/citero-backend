package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.MfaEnableResponseDto;
import com.nanopiva.citero.dto.user.MfaSetupResponseDto;
import com.nanopiva.citero.dto.user.MfaStatusResponseDto;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Segundo factor (TOTP) por usuario: enrolamiento, activación, verificación en login y
 * desactivación. Los códigos de recuperación se guardan sólo como hash SHA-256 y se consumen
 * al usarse.
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;

    @Value("${citero.auth.mfa.issuer:Citero}")
    private String issuer;

    @Transactional(readOnly = true)
    public MfaStatusResponseDto status(Long userId) {
        return MfaStatusResponseDto.builder()
                .enabled(Boolean.TRUE.equals(load(userId).getTotpEnabled()))
                .build();
    }

    /** Inicia el enrolamiento: genera y guarda un secreto (todavía sin activar). */
    @Transactional
    public MfaSetupResponseDto beginSetup(Long userId) {
        User user = load(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BadRequestException("El MFA ya está activo. Desactivalo antes de reconfigurarlo.");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);
        return MfaSetupResponseDto.builder()
                .secret(secret)
                .otpauthUri(totpService.otpauthUri(secret, user.getEmail(), issuer))
                .build();
    }

    /** Activa el MFA verificando un código del TOTP; devuelve los códigos de recuperación (una vez). */
    @Transactional
    public MfaEnableResponseDto enable(Long userId, String code) {
        User user = load(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BadRequestException("El MFA ya está activo.");
        }
        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            throw new BadRequestException("Primero iniciá el enrolamiento del MFA.");
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            throw new BadRequestException("El código es incorrecto.");
        }
        List<String> recoveryCodes = generateRecoveryCodes();
        user.setTotpRecoveryCodes(joinHashes(recoveryCodes));
        user.setTotpEnabled(true);
        userRepository.save(user);
        return MfaEnableResponseDto.builder().recoveryCodes(recoveryCodes).build();
    }

    /** Desactiva el MFA: exige contraseña actual y un código válido (TOTP o recuperación). */
    @Transactional
    public void disable(Long userId, String password, String code) {
        User user = load(userId);
        if (password == null || password.isBlank()
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("La contraseña es incorrecta.");
        }
        if (!verifyLoginCode(user, code)) {
            throw new BadRequestException("El código es incorrecto.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpRecoveryCodes(null);
        userRepository.save(user);
    }

    /** Verifica un código de login (TOTP o de recuperación). Consume el de recuperación si se usa. */
    @Transactional
    public boolean verifyLoginCode(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTotpEnabled())
                || user.getTotpSecret() == null || code == null) {
            return false;
        }
        if (totpService.verify(user.getTotpSecret(), code)) {
            return true;
        }
        return consumeRecoveryCode(user, code);
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));
    }

    private boolean consumeRecoveryCode(User user, String code) {
        String stored = user.getTotpRecoveryCodes();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        byte[] provided = hash(code).getBytes(StandardCharsets.US_ASCII);
        List<String> remaining = new ArrayList<>();
        boolean matched = false;
        for (String hash : stored.split(",")) {
            if (!matched && MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII), provided)) {
                matched = true; // se consume: no se agrega a los restantes
            } else {
                remaining.add(hash);
            }
        }
        if (matched) {
            user.setTotpRecoveryCodes(remaining.isEmpty() ? null : String.join(",", remaining));
            userRepository.save(user);
        }
        return matched;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(11);
            for (int j = 0; j < 10; j++) {
                if (j == 5) {
                    sb.append('-');
                }
                sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private String joinHashes(List<String> codes) {
        return codes.stream().map(this::hash).reduce((a, b) -> a + "," + b).orElse(null);
    }

    private String hash(String code) {
        try {
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo hashear el código de recuperación.", ex);
        }
    }
}

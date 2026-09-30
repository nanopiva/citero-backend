package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.OtpToken;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.security.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_OTPS_PER_TARGET = 5;
    private static final Duration OTP_TARGET_WINDOW = Duration.ofMinutes(15);

    public static final String PURPOSE_GUEST_VERIFICATION = "GUEST_VERIFICATION";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PURPOSE_CANCELLATION_VERIFICATION = "CANCELLATION_VERIFICATION";
    public static final String PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    private final OtpTokenRepository otpTokenRepository;
    private final EmailService emailService;
    private final RateLimitStore rateLimitStore;

    public OtpService(OtpTokenRepository otpTokenRepository, EmailService emailService,
                      RateLimitStore rateLimitStore) {
        this.otpTokenRepository = otpTokenRepository;
        this.emailService = emailService;
        this.rateLimitStore = rateLimitStore;
    }

    @Transactional
    public void generateAndSendOtp(String target, String purpose) {
        generateAndSendOtp(target, purpose, true);
    }

    /** Crea el OTP y, si {@code send}, lo envía. El flag permite no enviar sin cambiar el trabajo. */
    @Transactional
    public void generateAndSendOtp(String target, String purpose, boolean send) {
        // Cap por destinatario (anti email-bombing), además del límite por IP.
        if (!rateLimitStore.tryConsume(
                "otp-target:" + target, MAX_OTPS_PER_TARGET, OTP_TARGET_WINDOW)) {
            throw new BadRequestException(
                    "Pediste demasiados códigos. Esperá unos minutos e intentá de nuevo.");
        }

        // Solo el último código emitido debe ser válido: invalidamos los anteriores del mismo propósito.
        otpTokenRepository.markActiveAsUsed(target, purpose);

        String code = generateCode();

        OtpToken token = OtpToken.builder()
                .target(target)
                .code(code)
                .purpose(purpose)
                .expirationTime(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
                .isUsed(false)
                .attempts(0)
                .build();
        otpTokenRepository.save(token);

        if (send) {
            Map<String, Object> variables = Map.of("code", code);
            String templateName;
            String subject;

            if (PURPOSE_PASSWORD_RESET.equals(purpose)) {
                templateName = "emails/password-reset-email";
                subject = "Restablecé tu contraseña en Citero";
            } else if (PURPOSE_EMAIL_VERIFICATION.equals(purpose)) {
                templateName = "emails/verify-email";
                subject = "Verificá tu email en Citero";
            } else if (PURPOSE_CANCELLATION_VERIFICATION.equals(purpose)) {
                templateName = "emails/cancellation-verification-email";
                subject = "Código para cancelar tu turno en Citero";
            } else {
                templateName = "emails/otp-email";
                subject = "Tu código de verificación de Citero";
            }

            emailService.sendEmail(target, subject, templateName, variables);
        }

        log.info("OTP generado para {} con propósito {}", target, purpose);
    }

    /** Housekeeping diario de OTPs vencidos. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpired() {
        otpTokenRepository.deleteByExpirationTimeBefore(LocalDateTime.now());
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyOtp(String target, String code, String purpose) {
        OtpToken token = otpTokenRepository
                .findFirstByTargetAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(target, purpose)
                .orElseThrow(() -> new BadRequestException("El código es inválido o ya ha sido utilizado."));

        if (token.getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("El código ha expirado. Por favor, solicita uno nuevo.");
        }

        if (!token.getCode().equals(code)) {
            int attempts = (token.getAttempts() == null ? 0 : token.getAttempts()) + 1;
            token.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                otpTokenRepository.markActiveAsUsed(target, purpose);
                throw new BadRequestException("Demasiados intentos fallidos. Solicitá un código nuevo.");
            }
            otpTokenRepository.save(token);
            throw new BadRequestException("El código es inválido o ya ha sido utilizado.");
        }

        token.setIsUsed(true);
        otpTokenRepository.save(token);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
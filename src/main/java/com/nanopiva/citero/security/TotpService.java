package com.nanopiva.citero.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * TOTP (RFC 6238) para el segundo factor de autenticación. Verifica el código actual con una
 * ventana de ±1 paso (30s) y expone el secreto en Base32 para el provisioning (otpauth://).
 *
 * <p>Implementación propia y autocontenida (HMAC-SHA1 + Base32), validada con los vectores de
 * prueba del RFC 6238.</p>
 */
@Component
public class TotpService {

    private static final String HMAC = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20; // 160 bits
    private static final long TIME_STEP_SECONDS = 30;
    private static final int WINDOW_STEPS = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int digits;

    public TotpService(@Value("${citero.auth.mfa.digits:6}") int digits) {
        this.digits = Math.max(6, Math.min(8, digits));
    }

    /** Genera un secreto nuevo (160 bits) en Base32 sin padding. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** true si el código coincide con el paso actual ± {@value #WINDOW_STEPS}. */
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.chars().allMatch(Character::isDigit) || trimmed.length() != digits) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long counter = currentCounter();
        for (long offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
            String expected = codeFromKey(key, counter + offset, digits);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    trimmed.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Código TOTP actual para un secreto (requiere conocerlo). Útil para herramientas/tests;
     * la app nunca lo usa en el login, sólo verifica.
     */
    public String currentCode(String base32Secret) {
        return codeFromKey(base32Decode(base32Secret), currentCounter(), digits);
    }

    /** URI otpauth:// para apps de autenticación. */
    public String otpauthUri(String base32Secret, String account, String issuer) {
        String label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + digits
                + "&period=" + TIME_STEP_SECONDS
                + "&algorithm=SHA1";
    }

    private long currentCounter() {
        return System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
    }

    private String codeFromKey(byte[] key, long counter, int digits) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular el TOTP.", ex);
        }
    }

    // --- Visibles para tests ---

    String codeAt(String base32Secret, long epochSeconds, int digits) {
        return codeFromKey(base32Decode(base32Secret), epochSeconds / TIME_STEP_SECONDS, digits);
    }

    static String codeFromKeyForTest(byte[] key, long epochSeconds, int digits) {
        return new TotpService(digits).codeFromKey(key, epochSeconds / TIME_STEP_SECONDS, digits);
    }

    static String base32EncodeForTest(byte[] bytes) {
        return base32Encode(bytes);
    }

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String encoded) {
        String normalized = encoded.trim().replace("=", "").toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Secreto TOTP vacío.");
        }
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : normalized.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Secreto TOTP inválido.");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

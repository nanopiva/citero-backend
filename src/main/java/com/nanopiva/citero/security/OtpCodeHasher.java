package com.nanopiva.citero.security;

import com.nanopiva.citero.util.Emails;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Hashea los códigos OTP antes de persistirlos (HMAC-SHA256), de modo que un volcado de la
 * tabla {@code otp_tokens} no exponga códigos vigentes. La entrada se ata a
 * {@code purpose + target} para acotar el alcance del digest y la comparación es en tiempo
 * constante.
 *
 * <p>La clave sale de {@code citero.otp.secret} (Base64, >= 32 bytes). Si no se define un
 * secreto dedicado, cae al secreto del JWT para no impedir el arranque; en producción se
 * recomienda configurar {@code CITERO_OTP_SECRET} (separación de claves).</p>
 */
@Component
public class OtpCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final byte[] key;

    public OtpCodeHasher(@Value("${citero.otp.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("citero.otp.secret no está configurado.");
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            throw new IllegalStateException("citero.otp.secret debe estar codificado en Base64.", ex);
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("citero.otp.secret debe decodificar a al menos "
                    + MIN_KEY_BYTES + " bytes; actual: " + decoded.length + " bytes.");
        }
        this.key = decoded;
    }

    /** Digest hex (64 chars) del código atado al destinatario y al propósito. */
    public String hash(String target, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            String payload = purpose + "|" + Emails.normalize(target) + "|" + code;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo hashear el OTP.", ex);
        }
    }

    /** Compara el código provisto con el hash almacenado en tiempo constante. */
    public boolean matches(String target, String purpose, String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        String computed = hash(target, purpose, code);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}

package com.nanopiva.citero.support;

import com.nanopiva.citero.service.EmailService;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.List;
import java.util.Map;

/**
 * Utilidad de tests para recuperar el código OTP en claro: como en la DB sólo se guarda el
 * hash, el código se obtiene del email (mockeado) que {@code OtpService} intenta enviar.
 */
public final class OtpTestCodes {

    private OtpTestCodes() {
    }

    /** Último código OTP enviado al destinatario vía {@link EmailService#sendEmail}. */
    @SuppressWarnings("unchecked")
    public static String latestFor(EmailService emailService, String target) {
        List<Invocation> invocations = Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(invocation -> "sendEmail".equals(invocation.getMethod().getName()))
                .filter(invocation -> target.equals(invocation.getArgument(0)))
                .toList();
        if (invocations.isEmpty()) {
            throw new AssertionError("No se envió ningún OTP a " + target);
        }
        Invocation last = invocations.get(invocations.size() - 1);
        Map<String, Object> variables = (Map<String, Object>) last.getArgument(3);
        Object code = variables == null ? null : variables.get("code");
        if (code == null) {
            throw new AssertionError("El email de OTP no incluía el código");
        }
        return code.toString();
    }
}

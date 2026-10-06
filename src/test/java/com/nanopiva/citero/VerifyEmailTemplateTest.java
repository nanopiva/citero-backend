package com.nanopiva.citero;

import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyEmailTemplateTest extends IntegrationTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    void rendersVerifyEmailTemplateWithCode() {
        Context context = new Context();
        context.setVariable("code", "123456");

        String html = templateEngine.process("emails/verify-email", context);

        assertTrue(html.contains("123456"), "El código debe aparecer en el email");
        assertTrue(html.contains("Confirmá tu email"), "El encabezado debe estar presente");
    }
}

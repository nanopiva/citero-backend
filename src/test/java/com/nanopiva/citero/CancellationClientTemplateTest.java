package com.nanopiva.citero;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.nanopiva.citero.support.IntegrationTest;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationClientTemplateTest extends IntegrationTest {

    @Autowired
    private TemplateEngine templateEngine;

    private Map<String, Object> variables(boolean byBusiness) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("clientName", "Juan");
        vars.put("businessName", "Barbería Citero");
        vars.put("serviceName", "Corte de cabello");
        vars.put("staffName", "Ana");
        vars.put("date", "15 de octubre de 2026");
        vars.put("time", "10:00");
        vars.put("businessPhone", "+54 9 11 1234-5678");
        vars.put("businessUrl", "http://localhost:3000/negocio/barberia-citero");
        vars.put("byBusiness", byBusiness);
        return vars;
    }

    @Test
    void rendersClientInitiatedCancellation() {
        Context context = new Context();
        variables(false).forEach(context::setVariable);

        String html = templateEngine.process("emails/appointment-cancellation-client", context);

        assertTrue(html.contains("cancelada correctamente"));
        assertFalse(html.contains("El negocio canceló"));
    }

    @Test
    void rendersBusinessInitiatedCancellation() {
        Context context = new Context();
        variables(true).forEach(context::setVariable);

        String html = templateEngine.process("emails/appointment-cancellation-client", context);

        assertTrue(html.contains("El negocio canceló"));
        assertFalse(html.contains("cancelada correctamente"));
    }
}

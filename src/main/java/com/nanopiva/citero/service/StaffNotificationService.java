package com.nanopiva.citero.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class StaffNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StaffNotificationService.class);

    private final EmailService emailService;
    private final String frontendUrl;

    public StaffNotificationService(
            EmailService emailService,
            @Value("${citero.frontend.url}") String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Envía la invitación al staff dependiendo de si ya tiene cuenta en Citero o no.
     *
     * @param email          Correo del miembro del staff.
     * @param customName     Nombre personalizado (puede ser null).
     * @param businessName   Nombre del negocio que lo invita.
     * @param isRegistered   True si el email ya existe en la tabla de usuarios.
     * @param invitationToken Token de la invitación (null si el email ya está registrado).
     */
    public void sendStaffInvitation(String email, String customName, String businessName,
                                    boolean isRegistered, String invitationToken) {
        try {
            // Helper para obtener un nombre legible si no hay customName
            String displayName = (customName != null && !customName.trim().isEmpty())
                    ? customName
                    : email.split("@")[0];

            if (isRegistered) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("staffName", displayName);
                vars.put("businessName", businessName);
                vars.put("loginUrl", frontendUrl + "/login");

                emailService.sendEmail(
                        email,
                        "Has sido invitado al equipo de " + businessName,
                        "emails/staff-invitation-registered",
                        vars
                );
            } else {
                Map<String, Object> vars = new HashMap<>();
                vars.put("staffName", displayName);
                vars.put("businessName", businessName);
                vars.put("email", email);
                vars.put("registerUrl", frontendUrl + "/registro?email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + (invitationToken != null ? "&invitacion=" + invitationToken : ""));

                emailService.sendEmail(
                        email,
                        "Te invitamos a unirte al equipo de " + businessName,
                        "emails/staff-invitation-guest",
                        vars
                );
            }
            log.info("Invitación de staff enviada exitosamente a: {}", email);
        } catch (Exception e) {
            // Capturamos el error para no interrumpir el flujo de creación del staff
            log.error("Error al preparar/enviar la invitación de staff a {}: {}", email, e.getMessage());
        }
    }
}
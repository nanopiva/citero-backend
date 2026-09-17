package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Service;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.util.StaffUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@org.springframework.stereotype.Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);

    private final EmailService emailService;

    public AppointmentNotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Dispara los emails de confirmación para el cliente, el dueño del negocio
     * y el staff asignado (solo si es distinto del dueño).
     */
    public void sendAppointmentConfirmation(Appointment appointment, String frontendUrl) {
        try {
            User client = appointment.getClient();
            Staff staff = appointment.getStaff();
            Business business = staff.getBusiness();
            User owner = business.getOwner();
            Service service = appointment.getService();

            // Formatear fecha y hora para mostrar en el email
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "AR"));
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String formattedDate = appointment.getStartTime().format(dateFormatter);
            String formattedTime = appointment.getStartTime().format(timeFormatter);

            // Helpers para nombres legibles
            String clientDisplayName = client.getEmail().split("@")[0];
            String staffDisplayName = StaffUtils.displayName(staff);
            String ownerDisplayName = owner.getEmail().split("@")[0];

            Map<String, Object> clientVars = new HashMap<>();
            clientVars.put("clientName", clientDisplayName);
            clientVars.put("businessName", business.getName());
            clientVars.put("serviceName", service.getName());
            clientVars.put("staffName", staffDisplayName);
            clientVars.put("date", formattedDate);
            clientVars.put("time", formattedTime);
            clientVars.put("address", business.getAddress() != null ? business.getAddress() : "Dirección no disponible");
            clientVars.put("manageUrl", frontendUrl + "/turnos/gestionar?id=" + appointment.getId());

            emailService.sendEmail(
                    client.getEmail(),
                    "Confirmación de tu turno en " + business.getName(),
                    "emails/appointment-confirmation-client",
                    clientVars
            );

            Map<String, Object> ownerVars = new HashMap<>();
            ownerVars.put("ownerName", ownerDisplayName);
            ownerVars.put("clientName", clientDisplayName);
            ownerVars.put("businessName", business.getName());
            ownerVars.put("serviceName", service.getName());
            ownerVars.put("staffName", staffDisplayName);
            ownerVars.put("date", formattedDate);
            ownerVars.put("time", formattedTime);
            ownerVars.put("dashboardUrl", frontendUrl + "/agenda");

            emailService.sendEmail(
                    owner.getEmail(),
                    "Nueva reserva en " + business.getName(),
                    "emails/appointment-confirmation-owner",
                    ownerVars
            );

            String staffEmail = StaffUtils.email(staff);
            if (staffEmail != null && !staffEmail.equalsIgnoreCase(owner.getEmail())) {
                Map<String, Object> staffVars = new HashMap<>();
                staffVars.put("staffName", staffDisplayName);
                staffVars.put("businessName", business.getName());
                staffVars.put("clientName", clientDisplayName);
                staffVars.put("serviceName", service.getName());
                staffVars.put("date", formattedDate);
                staffVars.put("time", formattedTime);
                staffVars.put("address", business.getAddress() != null ? business.getAddress() : "Dirección no disponible");
                // Link a la agenda (requiere autenticación, no accesible por OTP)
                staffVars.put("agendaUrl", frontendUrl + "/agenda");

                emailService.sendEmail(
                        staffEmail,
                        "Nuevo turno asignado en " + business.getName(),
                        "emails/appointment-confirmation-staff",
                        staffVars
                );

                log.info("Email de confirmación disparado al staff ({}) para el turno ID {}",
                        staffEmail, appointment.getId());
            } else {
                log.debug("Staff asignado al turno ID {} es el mismo que el dueño (o no tiene email). " +
                        "Se omite envío de email adicional.", appointment.getId());
            }

            log.info("Emails de confirmación disparados para el turno ID {}", appointment.getId());
        } catch (Exception e) {
            // Capturamos cualquier error para que no rompa el flujo principal de creación del turno
            log.error("Error al preparar emails de confirmación para el turno ID {}: {}", appointment.getId(), e.getMessage());
        }
    }

    /**
     * Dispara los emails de confirmación de cancelación tanto para el cliente como para el dueño del negocio.
     * Se usa cuando la cancelación la inició el cliente o un invitado.
     */
    public void sendAppointmentCancellation(Appointment appointment, String frontendUrl) {
        try {
            Business business = appointment.getStaff().getBusiness();
            User owner = business.getOwner();
            Service service = appointment.getService();

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "AR"));
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            sendCancellationToClient(appointment, frontendUrl, false);

            Map<String, Object> ownerVars = new HashMap<>();
            ownerVars.put("ownerName", owner.getEmail().split("@")[0]);
            ownerVars.put("clientName", appointment.getClient().getEmail().split("@")[0]);
            ownerVars.put("businessName", business.getName());
            ownerVars.put("serviceName", service.getName());
            ownerVars.put("staffName", StaffUtils.displayName(appointment.getStaff()));
            ownerVars.put("date", appointment.getStartTime().format(dateFormatter));
            ownerVars.put("time", appointment.getStartTime().format(timeFormatter));
            ownerVars.put("dashboardUrl", frontendUrl + "/agenda");

            emailService.sendEmail(
                    owner.getEmail(),
                    "Turno cancelado en " + business.getName(),
                    "emails/appointment-cancellation-owner",
                    ownerVars
            );

            log.info("Emails de cancelación disparados para el turno ID {}", appointment.getId());
        } catch (Exception e) {
            log.error("Error al preparar emails de cancelación para el turno ID {}: {}", appointment.getId(), e.getMessage());
        }
    }

    /**
     * Notifica al cliente que el NEGOCIO canceló su turno.
     *
     * <p>A diferencia de {@link #sendAppointmentCancellation}, no envía email al dueño
     * (fue quien realizó la cancelación).</p>
     */
    public void sendAppointmentCancellationByBusiness(Appointment appointment, String frontendUrl) {
        try {
            sendCancellationToClient(appointment, frontendUrl, true);
            log.info("Email de cancelación por parte del negocio disparado para el turno ID {}", appointment.getId());
        } catch (Exception e) {
            log.error("Error al preparar email de cancelación por negocio para el turno ID {}: {}", appointment.getId(), e.getMessage());
        }
    }

    /**
     * Construye y envía el email de cancelación al cliente.
     *
     * @param byBusiness {@code true} si la cancelación la hizo el negocio (cambia el mensaje),
     *                   {@code false} si la inició el cliente/invitado.
     */
    private void sendCancellationToClient(Appointment appointment, String frontendUrl, boolean byBusiness) {
        User client = appointment.getClient();
        Staff staff = appointment.getStaff();
        Business business = staff.getBusiness();
        Service service = appointment.getService();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "AR"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        Map<String, Object> clientVars = new HashMap<>();
        clientVars.put("clientName", client.getEmail().split("@")[0]);
        clientVars.put("businessName", business.getName());
        clientVars.put("serviceName", service.getName());
        clientVars.put("staffName", StaffUtils.displayName(staff));
        clientVars.put("date", appointment.getStartTime().format(dateFormatter));
        clientVars.put("time", appointment.getStartTime().format(timeFormatter));
        clientVars.put("businessPhone", business.getPhone() != null ? business.getPhone() : "");
        clientVars.put("businessUrl", frontendUrl + "/negocio/" + business.getSlug());
        clientVars.put("byBusiness", byBusiness);

        emailService.sendEmail(
                client.getEmail(),
                "Confirmación de cancelación - " + business.getName(),
                "emails/appointment-cancellation-client",
                clientVars
        );
    }

    /**
     * Dispara el email de recordatorio al cliente.
     *
     * @param appointment  El turno a recordar.
     * @param reminderType "24h" o "2h" para personalizar el mensaje y el asunto.
     * @param frontendUrl  URL base del frontend para generar el link de gestión.
     * @return {@code true} si el email se envió correctamente, {@code false} en caso contrario.
     */
    public boolean sendAppointmentReminder(Appointment appointment, String reminderType, String frontendUrl) {
        try {
            User client = appointment.getClient();
            Staff staff = appointment.getStaff();
            Business business = staff.getBusiness();
            Service service = appointment.getService();

            // Formatear fecha y hora para mostrar en el email
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "AR"));
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String formattedDate = appointment.getStartTime().format(dateFormatter);
            String formattedTime = appointment.getStartTime().format(timeFormatter);

            String clientDisplayName = client.getEmail().split("@")[0];
            String staffDisplayName = StaffUtils.displayName(staff);

            Map<String, Object> clientVars = new HashMap<>();
            clientVars.put("clientName", clientDisplayName);
            clientVars.put("businessName", business.getName());
            clientVars.put("serviceName", service.getName());
            clientVars.put("staffName", staffDisplayName);
            clientVars.put("date", formattedDate);
            clientVars.put("time", formattedTime);
            clientVars.put("address", business.getAddress() != null ? business.getAddress() : "Dirección no disponible");
            clientVars.put("manageUrl", frontendUrl + "/turnos/gestionar?id=" + appointment.getId());
            clientVars.put("reminderType", reminderType);

            // Asunto dinámico según el tipo de recordatorio
            String subject = reminderType.equals("24h")
                    ? "Recordatorio: Tu turno es mañana en " + business.getName()
                    : "Recordatorio: Tu turno es en 2 horas en " + business.getName();

            Boolean sent = emailService.sendEmail(
                    client.getEmail(),
                    subject,
                    "emails/appointment-reminder-client",
                    clientVars
            ).join();

            if (!Boolean.TRUE.equals(sent)) {
                log.warn("El email de recordatorio ({}) para el turno ID {} no pudo enviarse.", reminderType, appointment.getId());
                return false;
            }

            log.info("Email de recordatorio ({}) disparado para el turno ID {}", reminderType, appointment.getId());
            return true;
        } catch (Exception e) {
            // Capturamos cualquier error para que no rompa el flujo del scheduler
            log.error("Error al preparar email de recordatorio para el turno ID {}: {}", appointment.getId(), e.getMessage(), e);
            return false;
        }
    }
}
package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.util.BusinessTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentNotificationService appointmentNotificationService;
    private final BusinessConfigService businessConfigService;

    @Value("${citero.frontend.url}")
    private String frontendUrl;

    /**
     * Procesa los recordatorios pendientes para las ventanas de 24h y 2h.
     *
     * <p>No se ejecuta dentro de una única transacción: los candidatos se cargan con
     * sus asociaciones ya inicializadas y cada marcado de "enviado" se persiste en su
     * propia transacción. Así el envío de emails (llamada de red con reintentos) no
     * mantiene abierta una conexión a base de datos.</p>
     */
    public void processReminders() {
        // La ventana exacta de 24h/2h depende de la zona horaria del negocio, así que se
        // evalúa turno por turno. La consulta trae candidatos en una ventana amplia (cubre
        // todos los husos) y luego se filtra fino con la hora local de cada negocio.
        LocalDateTime reference = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = reference.minusHours(12);
        LocalDateTime to = reference.plusHours(39);

        List<Appointment> candidates = appointmentRepository.findByStatusAndStartTimeBetween(
                Appointment.AppointmentStatus.CONFIRMED, from, to
        );

        for (Appointment appt : candidates) {
            LocalDateTime now = BusinessTime.now(appt.getStaff().getBusiness());
            if (isWithinReminderWindow(appt.getStartTime(), now, 24)) {
                processReminder(appt, "24h");
            }
            if (isWithinReminderWindow(appt.getStartTime(), now, 2)) {
                processReminder(appt, "2h");
            }
        }
    }

    /**
     * Indica si el turno arranca dentro de la ventana objetivo (p. ej. 24h) con una
     * tolerancia de 15 minutos, coherente con el cron de 15 minutos.
     */
    private boolean isWithinReminderWindow(LocalDateTime start, LocalDateTime now, int hours) {
        LocalDateTime from = now.plusHours(hours).minusMinutes(15);
        LocalDateTime to = now.plusHours(hours).plusMinutes(15);
        return !start.isBefore(from) && !start.isAfter(to);
    }

    private void processReminder(Appointment appt, String type) {
        // Doble check de seguridad a nivel de entidad para evitar duplicados
        if ((type.equals("24h") && appt.getReminder24hSentAt() != null) ||
                (type.equals("2h") && appt.getReminder2hSentAt() != null)) {
            return;
        }

        Business business = appt.getStaff().getBusiness();
        BusinessConfig config = businessConfigService.getConfigEntityByBusinessId(business.getId());

        if (config == null || !Boolean.TRUE.equals(config.getEnableReminders())) {
            return;
        }

        boolean isEnabled = type.equals("24h") ?
                Boolean.TRUE.equals(config.getReminder24hEnabled()) :
                Boolean.TRUE.equals(config.getReminder2hEnabled());
        if (!isEnabled) {
            return;
        }

        LocalDateTime now = BusinessTime.now(business);

        // Claim atómico: si otra instancia o ejecución ya lo tomó, no se envía de nuevo.
        boolean claimed = type.equals("24h")
                ? appointmentRepository.claimReminder24h(appt.getId(), now) == 1
                : appointmentRepository.claimReminder2h(appt.getId(), now) == 1;
        if (!claimed) {
            return;
        }

        // Disparar el email y esperar el resultado real del envío
        boolean sent = appointmentNotificationService.sendAppointmentReminder(appt, type, frontendUrl);
        if (!sent) {
            // Liberamos el claim para reintentar en la próxima ejecución.
            if (type.equals("24h")) {
                appointmentRepository.releaseReminder24h(appt.getId());
            } else {
                appointmentRepository.releaseReminder2h(appt.getId());
            }
            log.warn("No se pudo enviar el recordatorio {} para el turno ID {}. Se reintentará en la próxima ejecución.",
                    type, appt.getId());
            return;
        }

        // La base ya quedó marcada por el claim; reflejamos el valor en la entidad en memoria.
        if (type.equals("24h")) {
            appt.setReminder24hSentAt(now);
        } else {
            appt.setReminder2hSentAt(now);
        }
        log.debug("Recordatorio {} marcado como enviado para el turno ID {}", type, appt.getId());
    }
}
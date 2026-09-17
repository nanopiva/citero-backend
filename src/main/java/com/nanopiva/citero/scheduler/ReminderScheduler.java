package com.nanopiva.citero.scheduler;

import com.nanopiva.citero.service.AppointmentReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "citero.reminder.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final AppointmentReminderService appointmentReminderService;

    /**
     * Se ejecuta cada 15 minutos.
     * En el minuto 0 de cada intervalo de 15 minutos (00:00, 00:15, 00:30, etc.)
     **/
    @Scheduled(cron = "0 */15 * * * *")
    public void processAppointmentReminders() {
        log.info("Iniciando ejecución programada de recordatorios de turnos...");
        try {
            appointmentReminderService.processReminders();
            log.info("Ejecución programada de recordatorios finalizada con éxito.");
        } catch (Exception e) {
            log.error("Error durante la ejecución programada de recordatorios: {}", e.getMessage(), e);
        }
    }
}
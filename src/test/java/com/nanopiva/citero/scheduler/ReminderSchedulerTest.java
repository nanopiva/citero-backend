package com.nanopiva.citero.scheduler;

import com.nanopiva.citero.service.AppointmentReminderService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * El scheduler está deshabilitado en el perfil de test
 * ({@code citero.reminder.scheduler.enabled=false}); este test lo habilita
 * explícitamente para verificar que delega en {@link AppointmentReminderService}.
 */
@TestPropertySource(properties = "citero.reminder.scheduler.enabled=true")
class ReminderSchedulerTest extends IntegrationTest {

    @MockitoBean
    private AppointmentReminderService appointmentReminderService;

    @Autowired
    private ReminderScheduler reminderScheduler;

    @Test
    void invocaAlServicioDeRecordatorios() {
        reminderScheduler.processAppointmentReminders();

        verify(appointmentReminderService, atLeastOnce()).processReminders();
    }

    @Test
    void noPropagaExcepcionesDelServicio() {
        doThrow(new RuntimeException("fallo simulado"))
                .when(appointmentReminderService).processReminders();

        assertDoesNotThrow(() -> reminderScheduler.processAppointmentReminders(),
                "El scheduler no debe propagar excepciones del servicio");
        verify(appointmentReminderService, atLeastOnce()).processReminders();
    }
}

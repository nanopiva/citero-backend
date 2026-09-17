package com.nanopiva.citero.service;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Transactional
class AppointmentReminderServiceTest extends IntegrationTest {

    @Autowired private AppointmentReminderService reminderService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    // Aísla el envío real de emails (Resend) durante los tests.
    @MockitoBean private EmailService emailService;

    private Appointment createAppointmentAt(LocalDateTime start, boolean alreadySent24h) {
        return createAppointmentAt(start, alreadySent24h, ZoneId.systemDefault().getId());
    }

    private Appointment createAppointmentAt(LocalDateTime start, boolean alreadySent24h, String timezone) {
        User owner = userRepository.save(User.builder()
                .email("owner-rem-" + System.nanoTime() + "@test.com").password("x").build());
        User client = userRepository.save(User.builder()
                .email("client-rem-" + System.nanoTime() + "@test.com").password("x").build());

        Business business = Business.builder()
                .owner(owner)
                .name("Recordatorios")
                .slug("recordatorios-" + System.nanoTime())
                .timezone(timezone)
                .build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        business = businessRepository.save(business);

        Staff staff = staffRepository.save(Staff.builder()
                .business(business)
                .customName("Ana")
                .build());

        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());

        Appointment appointment = Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();
        if (alreadySent24h) {
            appointment.setReminder24hSentAt(LocalDateTime.now());
        }
        return appointmentRepository.save(appointment);
    }

    @Test
    void marcaRecordatorio24hComoEnviado() {
        when(emailService.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Appointment appointment = createAppointmentAt(LocalDateTime.now().plusHours(24), false);

        reminderService.processReminders();

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertNotNull(reloaded.getReminder24hSentAt(), "Debe marcar el recordatorio de 24h como enviado");
    }

    @Test
    void marcaRecordatorio2hComoEnviado() {
        when(emailService.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Appointment appointment = createAppointmentAt(LocalDateTime.now().plusHours(2), false);

        reminderService.processReminders();

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertNotNull(reloaded.getReminder2hSentAt(), "Debe marcar el recordatorio de 2h como enviado");
    }

    @Test
    void noReenviaSiYaEstabaMarcado() {
        Appointment appointment = createAppointmentAt(LocalDateTime.now().plusHours(24), true);

        reminderService.processReminders();

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertNotNull(reloaded.getReminder24hSentAt(), "El recordatorio ya marcado no debe modificarse");
    }

    @Test
    void elClaimDeRecordatorioEsAtomico() {
        Appointment appointment = createAppointmentAt(LocalDateTime.now().plusHours(24), false);
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, appointmentRepository.claimReminder24h(appointment.getId(), now),
                "El primer claim debe marcar la fila");
        assertEquals(0, appointmentRepository.claimReminder24h(appointment.getId(), now),
                "El segundo claim no debe marcar nada (ya estaba tomado)");
    }

    @Test
    void usaLaZonaDelNegocioParaCalcularLaVentanaDe24h() {
        when(emailService.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        // El turno está a 24h según la zona del negocio (Tokio), no según la del servidor.
        Appointment appointment = createAppointmentAt(
                LocalDateTime.now(tokyo).plusHours(24), false, tokyo.getId());

        reminderService.processReminders();

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertNotNull(reloaded.getReminder24hSentAt(),
                "Debe calcular la ventana con la zona del negocio, no con la del servidor");
    }

    @Test
    void noMarcaSiElEnvioFalla() {
        when(emailService.sendEmail(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(false));

        Appointment appointment = createAppointmentAt(LocalDateTime.now().plusHours(24), false);

        reminderService.processReminders();

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertNull(reloaded.getReminder24hSentAt(),
                "Si el envío falla, no debe marcarse como enviado para reintentar");
    }
}

package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.appointment.AvailabilityResponseDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AvailabilityServiceTest extends IntegrationTest {

    @Autowired private AvailabilityService availabilityService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleRepository scheduleRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .email(tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private Business newBusiness(String tag) {
        User owner = newUser("owner-" + tag);
        Business business = Business.builder()
                .owner(owner)
                .name("Negocio " + tag)
                .slug("biz-" + tag + "-" + System.nanoTime())
                .build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    private com.nanopiva.citero.entity.Service newService(Business business) {
        return serviceRepository.save(com.nanopiva.citero.entity.Service.builder()
                .business(business)
                .name("Corte")
                .durationMinutes(30)
                .price(BigDecimal.TEN)
                .build());
    }

    private Staff newStaff(Business business, com.nanopiva.citero.entity.Service service) {
        return staffRepository.save(Staff.builder()
                .business(business)
                .customName("Ana")
                .services(new HashSet<>(Set.of(service)))
                .build());
    }

    private Appointment newAppointment(User client, Staff staff, com.nanopiva.citero.entity.Service service,
                                       LocalDateTime start) {
        return appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(service.getDurationMinutes()))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build());
    }

    @Test
    void generaSlotsAlineadosALaGrilla() {
        Business business = newBusiness("slots");
        com.nanopiva.citero.entity.Service service = newService(business);
        newStaff(business, service);
        LocalDate date = LocalDate.now().plusDays(1);

        AvailabilityResponseDto response = availabilityService.getAvailableSlots(
                business.getId(), service.getId(), null, date);

        List<LocalTime> slots = response.getAvailableSlots();
        assertFalse(slots.isEmpty(), "Debe generar slots dentro del horario de atención");
        assertEquals(LocalTime.of(9, 0), slots.get(0), "El primer slot debe ser la apertura");
        assertTrue(slots.stream()
                        .allMatch(t -> Duration.between(LocalTime.of(9, 0), t).toMinutes() % 15 == 0),
                "Todos los slots deben estar alineados a la grilla de 15 minutos");
        LocalTime last = slots.get(slots.size() - 1);
        assertFalse(last.plusMinutes(30).isAfter(LocalTime.of(18, 0)),
                "El último slot debe entrar completo en el horario");
    }

    @Test
    void franjaFueraDeHorarioEsRechazada() {
        Business business = newBusiness("horario");
        com.nanopiva.citero.entity.Service service = newService(business);
        LocalDate date = LocalDate.now().plusDays(1);

        assertThrows(BadRequestException.class, () -> availabilityService.validateSlotRules(
                business, service, LocalDateTime.of(date, LocalTime.of(8, 0))),
                "Un slot antes de la apertura debe rechazarse");

        assertThrows(BadRequestException.class, () -> availabilityService.validateSlotRules(
                business, service, LocalDateTime.of(date, LocalTime.of(9, 10))),
                "Un slot desalineado de la grilla debe rechazarse");

        assertThrows(BadRequestException.class, () -> availabilityService.validateSlotRules(
                business, service, LocalDateTime.of(date, LocalTime.of(17, 45))),
                "Un slot que termina después del cierre debe rechazarse");
    }

    @Test
    void diaCerradoNoOfreceSlots() {
        Business business = newBusiness("cerrado");
        com.nanopiva.citero.entity.Service service = newService(business);
        newStaff(business, service);
        LocalDate date = LocalDate.now().plusDays(1);

        scheduleRepository.save(BusinessSchedule.builder()
                .business(business)
                .dayOfWeek(BusinessSchedule.DayOfWeek.valueOf(date.getDayOfWeek().name()))
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .isClosed(true)
                .build());

        AvailabilityResponseDto response = availabilityService.getAvailableSlots(
                business.getId(), service.getId(), null, date);

        assertTrue(response.getAvailableSlots().isEmpty(), "Un día cerrado no debe ofrecer slots");
    }

    @Test
    void slotOcupadoPorTurnoExistenteNoSeOfrece() {
        Business business = newBusiness("ocupado");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-ocupado");
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(10, 0));

        newAppointment(client, staff, service, start);

        AvailabilityResponseDto response = availabilityService.getAvailableSlots(
                business.getId(), service.getId(), staff.getId(), date);
        List<LocalTime> slots = response.getAvailableSlots();

        assertFalse(slots.contains(LocalTime.of(10, 0)), "No debe ofrecer el slot ocupado");
        assertFalse(slots.contains(LocalTime.of(9, 45)), "No debe ofrecer un slot que solapa por delante");
        assertFalse(slots.contains(LocalTime.of(10, 15)), "No debe ofrecer un slot que solapa por detrás");
        assertTrue(slots.contains(LocalTime.of(10, 30)), "Debe ofrecer el slot inmediatamente posterior");
    }

    @Test
    void assignAvailableStaffElijeAlLibre() {
        Business business = newBusiness("assign");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staffA = newStaff(business, service);
        Staff staffB = newStaff(business, service);
        User client = newUser("client-assign");
        LocalDate date = LocalDate.now().plusDays(1);

        newAppointment(client, staffA, service, LocalDateTime.of(date, LocalTime.of(10, 0)));

        Staff selected = availabilityService.assignAvailableStaff(
                business, service, LocalDateTime.of(date, LocalTime.of(10, 15)));

        assertEquals(staffB.getId(), selected.getId(), "Debe elegir al profesional que está libre");
    }

    @Test
    void assignAvailableStaffDesempataPorMenorId() {
        Business business = newBusiness("assign-tie");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staffA = newStaff(business, service);
        newStaff(business, service);
        LocalDate date = LocalDate.now().plusDays(1);

        Staff selected = availabilityService.assignAvailableStaff(
                business, service, LocalDateTime.of(date, LocalTime.of(11, 0)));

        assertEquals(staffA.getId(), selected.getId(),
                "Con igual carga horaria debe elegir el profesional de menor id");
    }

    @Test
    void assignAvailableStaffSinDisponiblesLanza() {
        Business business = newBusiness("assign-full");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-assign-full");
        LocalDate date = LocalDate.now().plusDays(1);
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(10, 0));

        newAppointment(client, staff, service, start);

        assertThrows(BadRequestException.class, () ->
                availabilityService.assignAvailableStaff(business, service, start));
    }
}

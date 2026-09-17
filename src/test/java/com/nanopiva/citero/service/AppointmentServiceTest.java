package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.appointment.AppointmentCreateRequestDto;
import com.nanopiva.citero.dto.appointment.AppointmentResponseDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.OtpToken;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AppointmentServiceTest extends IntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ClientReputationRepository reputationRepository;
    @Autowired private OtpTokenRepository otpTokenRepository;

    // Aísla el envío real de emails (Resend) durante los tests.
    @MockitoBean private EmailService emailService;

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .email(tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private Business newBusiness(String tag, int toleranceHours) {
        User owner = newUser("owner-" + tag);
        Business business = Business.builder()
                .owner(owner)
                .name("Negocio " + tag)
                .slug("biz-" + tag + "-" + System.nanoTime())
                .build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(toleranceHours)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enablePenalties(true)
                .maxStrikes(3)
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
                                       LocalDateTime start, Appointment.AppointmentStatus status) {
        return appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(service.getDurationMinutes()))
                .status(status)
                .build());
    }

    private LocalDateTime futureSlot(int daysAhead, int hour) {
        return LocalDateTime.now().plusDays(daysAhead).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void crearTurnoOk() {
        Business business = newBusiness("create", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-create");
        LocalDateTime start = futureSlot(1, 10);

        AppointmentResponseDto result = appointmentService.createAppointment(client.getId(),
                AppointmentCreateRequestDto.builder()
                        .serviceId(service.getId())
                        .staffId(staff.getId())
                        .startTime(start)
                        .build());

        assertNotNull(result.getId(), "El turno debe persistirse con un id");
        assertEquals("CONFIRMED", result.getStatus(), "El turno nuevo debe quedar CONFIRMED");
        assertEquals(staff.getId(), result.getStaff().getId(), "El turno debe quedar asignado al staff elegido");
        assertEquals(service.getId(), result.getService().getId(), "El servicio debe coincidir con el solicitado");
        assertEquals(client.getId(), result.getClient().getId(), "El cliente debe ser el usuario autenticado");
        assertEquals(business.getName(), result.getBusinessName(), "El nombre del negocio debe coincidir");
    }

    @Test
    void conflictoPorSolapamientoEsRechazado() {
        Business business = newBusiness("overlap", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-overlap");
        LocalDateTime start = futureSlot(1, 10);

        newAppointment(client, staff, service, start, Appointment.AppointmentStatus.CONFIRMED);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                appointmentService.createAppointment(client.getId(),
                        AppointmentCreateRequestDto.builder()
                                .serviceId(service.getId())
                                .staffId(staff.getId())
                                .startTime(start.plusMinutes(15))
                                .build()));

        assertTrue(ex.getMessage().contains("ya no está disponible"),
                "El mensaje debe indicar que el horario ya no está disponible");
    }

    @Test
    void transicionDeEstadoInvalidaEsRechazada() {
        Business business = newBusiness("transition", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-transition");
        Appointment appointment = newAppointment(client, staff, service, futureSlot(1, 10),
                Appointment.AppointmentStatus.CONFIRMED);
        Long ownerId = business.getOwner().getId();

        appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.COMPLETED);

        // COMPLETED es terminal: no se puede volver a NO_SHOW.
        assertThrows(BadRequestException.class, () ->
                appointmentService.updateStatus(appointment.getId(), ownerId,
                        Appointment.AppointmentStatus.NO_SHOW));
    }

    @Test
    void cancelByClientAplicaStrikeSiEsTardia() {
        Business business = newBusiness("late", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-late");
        Appointment appointment = newAppointment(client, staff, service,
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        AppointmentResponseDto result = appointmentService.cancelByClient(appointment.getId(), client.getId());

        assertEquals("CANCELLED", result.getStatus(), "El turno debe quedar cancelado");
        ClientReputation reputation = reputationRepository
                .findByClientAndBusiness(client, business)
                .orElseThrow(() -> new AssertionError("Debe existir reputación tras la cancelación tardía"));
        assertEquals(1, reputation.getStrikeCount(), "La cancelación tardía debe aplicar un strike");
    }

    @Test
    void cancelByClientNoAplicaStrikeSiEsAnticipada() {
        Business business = newBusiness("early", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-early");
        Appointment appointment = newAppointment(client, staff, service,
                LocalDateTime.now().plusHours(48).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        AppointmentResponseDto result = appointmentService.cancelByClient(appointment.getId(), client.getId());

        assertEquals("CANCELLED", result.getStatus(), "El turno debe quedar cancelado");
        assertTrue(reputationRepository.findByClientAndBusiness(client, business).isEmpty(),
                "Una cancelación fuera de tolerancia no debe generar strike");
    }

    @Test
    void cancelByGuestConOtpValido() {
        Business business = newBusiness("guest", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User guest = newUser("guest-cancel");
        Appointment appointment = newAppointment(guest, staff, service,
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        otpTokenRepository.save(OtpToken.builder()
                .target(guest.getEmail())
                .code("123456")
                .purpose(OtpService.PURPOSE_CANCELLATION_VERIFICATION)
                .expirationTime(LocalDateTime.now().plusMinutes(10))
                .isUsed(false)
                .build());

        AppointmentResponseDto result = appointmentService.cancelByGuest(
                appointment.getId(), guest.getEmail(), "123456");

        assertEquals("CANCELLED", result.getStatus(), "El turno debe cancelarse con un OTP válido");
    }

    @Test
    void cancelByGuestRechazaOtpInvalido() {
        Business business = newBusiness("guest-bad", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User guest = newUser("guest-bad");
        Appointment appointment = newAppointment(guest, staff, service,
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        assertThrows(BadRequestException.class, () ->
                appointmentService.cancelByGuest(appointment.getId(), guest.getEmail(), "000000"));
    }

    @Test
    void cancelByGuestRechazaEmailQueNoCoincide() {
        Business business = newBusiness("guest-mismatch", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User guest = newUser("guest-mismatch");
        Appointment appointment = newAppointment(guest, staff, service,
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        assertThrows(BadRequestException.class, () ->
                appointmentService.cancelByGuest(appointment.getId(), "otro@test.com", "123456"));
    }

    @Test
    void ownerAjenoNoPuedeConsultarAgenda() {
        Business business = newBusiness("perm", 24);
        User foreignOwner = newUser("foreign-owner");

        assertThrows(ForbiddenException.class, () ->
                appointmentService.getAppointments(foreignOwner.getId(), business.getId(), null, null, null, Pageable.unpaged()));
    }

    @Test
    void listadosFiltranPorClienteNegocioYStaff() {
        Business business = newBusiness("list", 24);
        com.nanopiva.citero.entity.Service service = newService(business);
        User staffUser = newUser("staff-list");
        Staff staff = newStaff(business, service);
        staff.setUser(staffUser);
        staffRepository.save(staff);
        User client = newUser("client-list");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        newAppointment(client, staff, service, futureSlot(1, 10), Appointment.AppointmentStatus.CONFIRMED);
        newAppointment(client, staff, service, futureSlot(1, 11), Appointment.AppointmentStatus.COMPLETED);
        newAppointment(client, staff, service, futureSlot(2, 10), Appointment.AppointmentStatus.CONFIRMED);

        Long ownerId = business.getOwner().getId();

        assertEquals(3, appointmentService.getAppointmentsByClient(client.getId(), Pageable.unpaged()).getContent().size(),
                "El cliente debe ver sus 3 turnos");

        List<AppointmentResponseDto> delDia = appointmentService
                .getAppointments(ownerId, business.getId(), null, tomorrow, null, Pageable.unpaged())
                .getContent();
        assertEquals(2, delDia.size(), "Sólo debe devolver los turnos del día indicado");

        List<AppointmentResponseDto> confirmadosDelDia = appointmentService
                .getAppointments(ownerId, business.getId(), null, tomorrow, Appointment.AppointmentStatus.CONFIRMED, Pageable.unpaged())
                .getContent();
        assertEquals(1, confirmadosDelDia.size(), "Debe filtrar por estado CONFIRMED");
        assertEquals("CONFIRMED", confirmadosDelDia.get(0).getStatus());

        List<AppointmentResponseDto> porStaff = appointmentService
                .getAppointments(ownerId, business.getId(), staff.getId(), null, null, Pageable.unpaged())
                .getContent();
        assertEquals(3, porStaff.size(), "El filtro por staff debe devolver sus 3 turnos");

        List<AppointmentResponseDto> agendaStaff = appointmentService
                .getAppointmentsForStaff(staffUser.getId(), business.getId(), null, Pageable.unpaged())
                .getContent();
        assertEquals(3, agendaStaff.size(), "El staff debe ver su propia agenda completa");
    }
}

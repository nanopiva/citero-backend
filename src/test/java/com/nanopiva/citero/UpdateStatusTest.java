package com.nanopiva.citero;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.service.AppointmentService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class UpdateStatusTest extends IntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ClientReputationRepository reputationRepository;

    private Appointment confirmedAppointment() {
        User owner = userRepository.save(User.builder().email("owner-status@test.com").password("x").build());
        User client = userRepository.save(User.builder().email("client-status@test.com").password("x").build());
        Business business = Business.builder().owner(owner).name("Status Test").slug("status-test").build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enablePenalties(true)
                .maxStrikes(3)
                .build();
        business.setConfig(config);
        business = businessRepository.save(business);

        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());

        LocalDateTime start = LocalDateTime.now().minusHours(1).withSecond(0).withNano(0);
        return appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build());
    }

    @Test
    void noShowIsIdempotentAndAppliesStrikeOnce() {
        Appointment appointment = confirmedAppointment();
        Long ownerId = appointment.getStaff().getBusiness().getOwner().getId();
        Long clientId = appointment.getClient().getId();
        Long businessId = appointment.getStaff().getBusiness().getId();

        appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.NO_SHOW);
        appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.NO_SHOW);

        ClientReputation reputation = reputationRepository
                .findByClientAndBusiness(appointment.getClient(), appointment.getStaff().getBusiness())
                .orElseThrow();
        assertEquals(1, reputation.getStrikeCount(), "El strike debe aplicarse una sola vez");
    }

    @Test
    void invalidTransitionIsRejected() {
        Appointment appointment = confirmedAppointment();
        Long ownerId = appointment.getStaff().getBusiness().getOwner().getId();

        appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.NO_SHOW);

        // NO_SHOW es terminal: no se puede pasar a COMPLETED
        assertThrows(BadRequestException.class, () ->
                appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.COMPLETED));
    }

    @Test
    void confirmOrCancelViaUpdateStatusIsRejected() {
        Appointment appointment = confirmedAppointment();
        Long ownerId = appointment.getStaff().getBusiness().getOwner().getId();

        assertThrows(BadRequestException.class, () ->
                appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.CANCELLED));
        assertThrows(BadRequestException.class, () ->
                appointmentService.updateStatus(appointment.getId(), ownerId, Appointment.AppointmentStatus.CONFIRMED));
    }
}

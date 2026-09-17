package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.DashboardStatsDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
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
class DashboardServiceTest extends IntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ClientReputationRepository reputationRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("dash-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String uniqueSlug(String base) {
        return base + "-" + System.nanoTime();
    }

    private Business seedBusiness(User owner, String slug) {
        Business business = Business.builder().owner(owner).name("Negocio " + slug).slug(slug).build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enablePenalties(true)
                .maxStrikes(3)
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    private Appointment appointment(User client, Staff staff, com.nanopiva.citero.entity.Service service,
                                    LocalDateTime start, Appointment.AppointmentStatus status) {
        return Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(status)
                .build();
    }

    @Test
    void dashboardAggregatesTodayAppointmentsRevenueAndBlockedClients() {
        User owner = persistUser("owner");
        Business business = seedBusiness(owner, uniqueSlug("dash"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());
        User client = persistUser("client");

        LocalDateTime base = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        appointmentRepository.save(appointment(client, staff, service, base, Appointment.AppointmentStatus.CONFIRMED));
        appointmentRepository.save(appointment(client, staff, service, base.plusMinutes(30), Appointment.AppointmentStatus.COMPLETED));
        appointmentRepository.save(appointment(client, staff, service, base.plusMinutes(60), Appointment.AppointmentStatus.CANCELLED));
        appointmentRepository.save(appointment(client, staff, service, base.plusMinutes(90), Appointment.AppointmentStatus.NO_SHOW));

        reputationRepository.save(ClientReputation.builder()
                .client(client).business(business).strikeCount(3).isBlocked(true).build());
        User otherOwner = persistUser("other");
        Business otherBusiness = seedBusiness(otherOwner, uniqueSlug("dash-other"));
        reputationRepository.save(ClientReputation.builder()
                .client(client).business(otherBusiness).strikeCount(3).isBlocked(true).build());

        DashboardStatsDto stats = dashboardService.getDashboardStats(business.getId(), owner.getId());

        assertEquals(business.getId(), stats.getBusinessId(), "Debe corresponder al negocio consultado");
        assertEquals(4L, stats.getAppointmentsToday(), "Deben contarse los 4 turnos de hoy");
        assertEquals(4L, stats.getAppointmentsThisMonth(), "Deben contarse los turnos del mes");
        assertEquals(1L, stats.getAppointmentsPending(), "Solo 1 turno está CONFIRMED (pendiente)");
        assertEquals(0, stats.getEstimatedRevenueToday().compareTo(new BigDecimal("20")),
                "Ingreso de hoy: 10 (confirmed) + 10 (completed); CANCELLED y NO_SHOW no cuentan");
        assertEquals(0, stats.getEstimatedRevenueThisMonth().compareTo(new BigDecimal("20")),
                "El ingreso del mes incluye los mismos turnos");
        assertEquals(50.0, stats.getOccupancyRate(), "Ocupación: (1 confirmed + 1 completed) / 4 * 100");
        assertEquals(1L, stats.getBlockedClientsCount(), "Solo cuenta el cliente bloqueado de este negocio");
    }

    @Test
    void dashboardByNonOwnerIsRejected() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"));

        assertThrows(ForbiddenException.class, () -> dashboardService.getDashboardStats(business.getId(), intruder.getId()),
                "Solo el dueño puede ver el dashboard");
    }

    @Test
    void dashboardForUnknownBusinessFails() {
        User owner = persistUser("unknown");

        assertThrows(ResourceNotFoundException.class, () -> dashboardService.getDashboardStats(999_999L, owner.getId()),
                "Un negocio inexistente debe producir ResourceNotFoundException");
    }
}

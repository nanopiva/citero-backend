package com.nanopiva.citero;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.nanopiva.citero.support.IntegrationTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class LockQueryTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void staffLockQueriesExecuteAgainstH2() {
        User owner = userRepository.save(User.builder().email("owner-lock@test.com").password("x").build());
        Business business = businessRepository.save(Business.builder().owner(owner).name("Test Lock").slug("test-lock").build());
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        assertTrue(staffRepository.findByIdForUpdate(staff.getId()).isPresent());

        List<Staff> locked = staffRepository.findByBusinessForUpdate(business.getId());
        assertFalse(locked.isEmpty());
    }

    @Test
    void appointmentLockQueryExecutesAgainstH2() {
        User owner = userRepository.save(User.builder().email("owner-appt-lock@test.com").password("x").build());
        User client = userRepository.save(User.builder().email("client-appt-lock@test.com").password("x").build());
        Business business = businessRepository.save(Business.builder().owner(owner).name("Test Lock Appt").slug("test-lock-appt").build());
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build());

        assertTrue(appointmentRepository.findByIdForUpdate(appointment.getId()).isPresent());
    }
}

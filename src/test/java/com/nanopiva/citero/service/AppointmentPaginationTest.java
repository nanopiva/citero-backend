package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.appointment.AppointmentResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class AppointmentPaginationTest extends IntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @MockitoBean private EmailService emailService;

    @Test
    void agendaPaginadaDevuelveLaPaginaSinError() {
        User owner = userRepository.save(User.builder()
                .email("page-owner-" + System.nanoTime() + "@test.com").password("x").build());
        Business business = Business.builder().owner(owner).name("Page").slug("page-" + System.nanoTime()).build();
        business.setConfig(BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .build());
        business = businessRepository.save(business);

        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business).name("Corte").durationMinutes(30).price(BigDecimal.TEN).build());

        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        User client = userRepository.save(User.builder()
                .email("page-client-" + System.nanoTime() + "@test.com").password("x").build());

        LocalDate date = LocalDate.now().plusDays(1);
        for (int hour = 10; hour <= 12; hour++) {
            LocalDateTime start = date.atTime(hour, 0);
            appointmentRepository.save(Appointment.builder()
                    .client(client).staff(staff).service(service)
                    .startTime(start).endTime(start.plusMinutes(30))
                    .status(Appointment.AppointmentStatus.CONFIRMED).build());
        }

        Page<AppointmentResponseDto> page = appointmentService.getAppointments(
                owner.getId(), business.getId(), null, date, null, PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }
}

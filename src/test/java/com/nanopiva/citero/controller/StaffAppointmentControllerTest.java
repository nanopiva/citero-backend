package com.nanopiva.citero.controller;

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
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.EmailService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class StaffAppointmentControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @MockitoBean private EmailService emailService;

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

    private RequestPostProcessor auth(User user) {
        return user(UserDetailsImpl.build(user));
    }

    private LocalDateTime futureSlot(int daysAhead, int hour) {
        return LocalDateTime.now().plusDays(daysAhead).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void agendaDelStaffDevuelveSusTurnos() throws Exception {
        Business business = newBusiness("staff-ctrl");
        com.nanopiva.citero.entity.Service service = newService(business);
        User staffUser = newUser("staff-ctrl-user");
        Staff staff = staffRepository.save(Staff.builder()
                .business(business)
                .user(staffUser)
                .customName("Ana")
                .services(new HashSet<>(Set.of(service)))
                .build());
        User client = newUser("client-staff-ctrl");
        newAppointment(client, staff, service, futureSlot(1, 10));

        mockMvc.perform(get("/api/staff/appointments")
                        .param("businessId", business.getId().toString())
                        .with(auth(staffUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].staff.id").value(staff.getId()));
    }

    @Test
    void usuarioQueNoEsStaffEsRechazado() throws Exception {
        Business business = newBusiness("staff-ctrl-denied");
        newService(business);
        User other = newUser("no-staff");

        mockMvc.perform(get("/api/staff/appointments")
                        .param("businessId", business.getId().toString())
                        .with(auth(other)))
                .andExpect(status().isBadRequest());
    }
}

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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AppointmentControllerTest extends IntegrationTest {

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

    private RequestPostProcessor auth(User user) {
        return user(UserDetailsImpl.build(user));
    }

    private LocalDateTime futureSlot(int daysAhead, int hour) {
        return LocalDateTime.now().plusDays(daysAhead).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void crearTurnoPublicoDevuelveCreated() throws Exception {
        Business business = newBusiness("ctrl-create");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        LocalDateTime start = futureSlot(1, 10);
        String guestEmail = "guest-ctrl-" + System.nanoTime() + "@test.com";

        String body = "{\"serviceId\":" + service.getId()
                + ",\"staffId\":" + staff.getId()
                + ",\"startTime\":\"" + start + "\""
                + ",\"guestEmail\":\"" + guestEmail + "\"}";

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.client.email").value(guestEmail))
                .andExpect(jsonPath("$.staff.id").value(staff.getId()));
    }

    @Test
    void listarMisTurnosDevuelveLosDelCliente() throws Exception {
        Business business = newBusiness("ctrl-list");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-ctrl-list");
        newAppointment(client, staff, service, futureSlot(1, 10), Appointment.AppointmentStatus.CONFIRMED);

        mockMvc.perform(get("/api/appointments/my-appointments").with(auth(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void cancelarTurnoDevuelveCancelled() throws Exception {
        Business business = newBusiness("ctrl-cancel");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-ctrl-cancel");
        Appointment appointment = newAppointment(client, staff, service,
                LocalDateTime.now().plusHours(1).withSecond(0).withNano(0),
                Appointment.AppointmentStatus.CONFIRMED);

        mockMvc.perform(put("/api/appointments/" + appointment.getId() + "/cancel").with(auth(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void endpointPublicoDevuelveDetalleDelTurno() throws Exception {
        Business business = newBusiness("ctrl-public");
        com.nanopiva.citero.entity.Service service = newService(business);
        Staff staff = newStaff(business, service);
        User client = newUser("client-ctrl-public");
        Appointment appointment = newAppointment(client, staff, service, futureSlot(1, 10),
                Appointment.AppointmentStatus.CONFIRMED);

        mockMvc.perform(get("/api/appointments/public/" + appointment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value(business.getName()))
                .andExpect(jsonPath("$.serviceName").value(service.getName()))
                .andExpect(jsonPath("$.clientEmail").doesNotExist())
                .andExpect(jsonPath("$.ownedByViewer").value(false))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void misTurnosSinAutenticacionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/appointments/my-appointments"))
                .andExpect(status().is4xxClientError());
    }
}

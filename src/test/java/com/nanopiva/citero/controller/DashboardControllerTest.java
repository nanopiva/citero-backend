package com.nanopiva.citero.controller;

import com.jayway.jsonpath.JsonPath;
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
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DashboardControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("dc-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
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

    @Test
    void getDashboardReturnsAggregatedStats() throws Exception {
        User owner = persistUser("owner");
        Business business = seedBusiness(owner, uniqueSlug("dc"));
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
        appointmentRepository.save(Appointment.builder()
                .client(client).staff(staff).service(service)
                .startTime(base).endTime(base.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED).build());
        appointmentRepository.save(Appointment.builder()
                .client(client).staff(staff).service(service)
                .startTime(base.plusMinutes(30)).endTime(base.plusMinutes(60))
                .status(Appointment.AppointmentStatus.CANCELLED).build());

        MvcResult result = mockMvc.perform(get("/api/businesses/" + business.getId() + "/dashboard")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        assertEquals(business.getId().longValue(),
                ((Number) JsonPath.read(json, "$.businessId")).longValue(),
                "Debe corresponder al negocio consultado");
        assertEquals(2L, ((Number) JsonPath.read(json, "$.appointmentsToday")).longValue(),
                "Deben contarse los 2 turnos de hoy");
        assertEquals(1L, ((Number) JsonPath.read(json, "$.appointmentsPending")).longValue(),
                "Solo 1 turno está CONFIRMED");
        assertEquals(0, new BigDecimal(JsonPath.read(json, "$.estimatedRevenueToday").toString())
                        .compareTo(BigDecimal.TEN),
                "Solo el turno no cancelado suma ingreso");
    }

    @Test
    void getDashboardByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("dc-perm"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/dashboard")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }
}

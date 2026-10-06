package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión de autorización: cubre la escalada vertical (un STAFF contra acciones de OWNER)
 * y el aislamiento horizontal cross-tenant (un dueño contra el negocio de otro). En todos
 * los casos el backend debe rechazar con 403, no sólo ocultarlo en la UI.
 */
@AutoConfigureMockMvc
class AuthorizationRegressionTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("authz-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
    }

    private String slug(String base) {
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
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    private void seedStaff(Business business, User user) {
        staffRepository.save(Staff.builder()
                .business(business)
                .user(user)
                .customName("Staff")
                .build());
    }

    // --- Escalada vertical: STAFF no puede ejecutar acciones de OWNER ---

    @Test
    void staffNoPuedeCrearServicio() throws Exception {
        Business business = seedBusiness(persistUser("svc-owner"), slug("authz-svc"));
        User staffUser = persistUser("svc-staff");
        seedStaff(business, staffUser);

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/services")
                        .header("Authorization", bearer(staffUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte\",\"durationMinutes\":30,\"price\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffNoPuedeCambiarLaConfig() throws Exception {
        Business business = seedBusiness(persistUser("cfg-owner"), slug("authz-cfg"));
        User staffUser = persistUser("cfg-staff");
        seedStaff(business, staffUser);

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/config")
                        .header("Authorization", bearer(staffUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationMode\":\"AUTHENTICATED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffNoPuedeActualizarElNegocio() throws Exception {
        Business business = seedBusiness(persistUser("biz-owner"), slug("authz-biz"));
        User staffUser = persistUser("biz-staff");
        seedStaff(business, staffUser);

        mockMvc.perform(put("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(staffUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hackeado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffNoPuedeVerLosClientes() throws Exception {
        Business business = seedBusiness(persistUser("cli-owner"), slug("authz-cli"));
        User staffUser = persistUser("cli-staff");
        seedStaff(business, staffUser);

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/clients")
                        .header("Authorization", bearer(staffUser)))
                .andExpect(status().isForbidden());
    }

    // --- Aislamiento horizontal: un dueño no accede al negocio ajeno ---

    @Test
    void otroDuenioNoVeElDashboard() throws Exception {
        Business business = seedBusiness(persistUser("dash-owner"), slug("authz-dash"));
        User intruder = persistUser("dash-intruder");

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/dashboard")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void otroDuenioNoVeLosTurnos() throws Exception {
        Business business = seedBusiness(persistUser("appt-owner"), slug("authz-appt"));
        User intruder = persistUser("appt-intruder");

        mockMvc.perform(get("/api/appointments")
                        .param("businessId", business.getId().toString())
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void otroDuenioNoVeLosHorarios() throws Exception {
        Business business = seedBusiness(persistUser("sched-owner"), slug("authz-sched"));
        User intruder = persistUser("sched-intruder");

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void otroDuenioNoVeLasExcepciones() throws Exception {
        Business business = seedBusiness(persistUser("exc-owner"), slug("authz-exc"));
        User intruder = persistUser("exc-intruder");

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/schedule-exceptions")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void otroDuenioNoVeLosClientes() throws Exception {
        Business business = seedBusiness(persistUser("cli2-owner"), slug("authz-cli2"));
        User intruder = persistUser("cli2-intruder");

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/clients")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }
}

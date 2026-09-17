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
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ServiceCatalogControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("scc-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private com.nanopiva.citero.entity.Service seedService(Business business, String name) {
        return serviceRepository.save(com.nanopiva.citero.entity.Service.builder()
                .business(business)
                .name(name)
                .durationMinutes(30)
                .price(BigDecimal.TEN)
                .build());
    }

    @Test
    void createServiceReturns201() throws Exception {
        User owner = persistUser("create");
        Business business = seedBusiness(owner, uniqueSlug("scc-create"));

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/services")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte\",\"durationMinutes\":45,\"price\":15.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Corte"))
                .andExpect(jsonPath("$.durationMinutes").value(45));
    }

    @Test
    void createServiceByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("scc-perm"));

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/services")
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte\",\"durationMinutes\":30,\"price\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getServicesIsPublic() throws Exception {
        User owner = persistUser("list");
        Business business = seedBusiness(owner, uniqueSlug("scc-list"));
        seedService(business, "Corte");

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Corte"));
    }

    @Test
    void updateServiceReturnsUpdatedData() throws Exception {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("scc-update"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/services/" + service.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte VIP\",\"durationMinutes\":60,\"price\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corte VIP"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    @Test
    void deleteServiceReturns204() throws Exception {
        User owner = persistUser("delete");
        Business business = seedBusiness(owner, uniqueSlug("scc-delete"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        mockMvc.perform(delete("/api/businesses/" + business.getId() + "/services/" + service.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteServiceWithAppointmentsReturns409() throws Exception {
        User owner = persistUser("fk-owner");
        User client = persistUser("fk-client");
        Business business = seedBusiness(owner, uniqueSlug("scc-fk"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build());

        mockMvc.perform(delete("/api/businesses/" + business.getId() + "/services/" + service.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
    }
}

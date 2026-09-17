package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.service.StaffNotificationService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StaffControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;

    @MockitoBean private StaffNotificationService staffNotificationService;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("sc-" + tag + "-" + System.nanoTime() + "@test.com")
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
    void createStaffReturns201WithOrphanProfile() throws Exception {
        User owner = persistUser("create");
        Business business = seedBusiness(owner, uniqueSlug("sc-create"));
        String email = "sc-orphan-" + System.nanoTime() + "@test.com";

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/staff")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"customName\":\"Ana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userEmail").value(email))
                .andExpect(jsonPath("$.hasClaimedAccount").value(false));
    }

    @Test
    void createStaffByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("sc-perm"));

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/staff")
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sc-x-" + System.nanoTime() + "@test.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStaffIsPublicAndListsTeam() throws Exception {
        User owner = persistUser("list");
        Business business = seedBusiness(owner, uniqueSlug("sc-list"));
        staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customName").value("Ana"));
    }

    @Test
    void addSelfAsStaffReturns201() throws Exception {
        User owner = persistUser("self");
        Business business = seedBusiness(owner, uniqueSlug("sc-self"));

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/staff/me")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + owner.getEmail() + "\",\"customName\":\"Dueño\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasClaimedAccount").value(true));
    }

    @Test
    void updateStaffChangesName() throws Exception {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("sc-update"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/staff/" + staff.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"staff-update-" + System.nanoTime() + "@test.com\",\"customName\":\"Ana María\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customName").value("Ana María"));
    }

    @Test
    void assignServicesToStaffReturnsAssignedServices() throws Exception {
        User owner = persistUser("assign");
        Business business = seedBusiness(owner, uniqueSlug("sc-assign"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());
        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/staff/" + staff.getId() + "/services")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + service.getId() + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(1));
    }

    @Test
    void deleteStaffByOwnerReturns204() throws Exception {
        User owner = persistUser("delete");
        Business business = seedBusiness(owner, uniqueSlug("sc-delete"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        mockMvc.perform(delete("/api/businesses/" + business.getId() + "/staff/" + staff.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void leaveStaffReturns204() throws Exception {
        User owner = persistUser("leave-owner");
        User member = persistUser("leave-member");
        Business business = seedBusiness(owner, uniqueSlug("sc-leave"));
        staffRepository.save(Staff.builder().business(business).user(member).build());

        mockMvc.perform(delete("/api/businesses/" + business.getId() + "/staff/me")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
    }
}

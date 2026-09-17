package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BusinessConfigControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("bccc-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private String fullBody(String mode) {
        return "{\"reservationMode\":\"" + mode + "\",\"cancellationToleranceHours\":12,"
                + "\"enablePenalties\":false,\"maxStrikes\":5,"
                + "\"defaultOpeningTime\":\"08:00:00\",\"defaultClosingTime\":\"20:00:00\","
                + "\"enableReminders\":false,\"reminder24hEnabled\":false,\"reminder2hEnabled\":false}";
    }

    @Test
    void getConfigIsPublicAndReturnsDefaults() throws Exception {
        User owner = persistUser("get");
        Business business = seedBusiness(owner, uniqueSlug("bccc-get"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationMode").value("PUBLIC"))
                .andExpect(jsonPath("$.maxStrikes").value(3));
    }

    @Test
    void updateConfigByOwnerReturnsUpdatedConfig() throws Exception {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("bccc-update"));

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/config")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullBody("AUTHENTICATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationMode").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.maxStrikes").value(5))
                .andExpect(jsonPath("$.enableReminders").value(false));
    }

    @Test
    void updateConfigWithInvalidModeReturns400() throws Exception {
        User owner = persistUser("bad-mode");
        Business business = seedBusiness(owner, uniqueSlug("bccc-bad"));

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/config")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullBody("SOLO_INVITADOS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateConfigByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("bccc-perm"));

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/config")
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullBody("PUBLIC")))
                .andExpect(status().isForbidden());
    }
}

package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.security.jwt.JwtService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReputationControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ClientReputationRepository reputationRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("rc-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private ClientReputation seedReputation(User client, Business business, int strikes, boolean blocked) {
        return reputationRepository.save(ClientReputation.builder()
                .client(client).business(business).strikeCount(strikes).isBlocked(blocked).build());
    }

    @Test
    void getReputationsReturnsBusinessList() throws Exception {
        User owner = persistUser("list-owner");
        User client = persistUser("list-client");
        Business business = seedBusiness(owner, uniqueSlug("rc-list"));
        seedReputation(client, business, 1, false);

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/reputation")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].clientEmail").value(client.getEmail()));
    }

    @Test
    void getReputationCreatesItWhenMissing() throws Exception {
        User owner = persistUser("get-owner");
        User client = persistUser("get-client");
        Business business = seedBusiness(owner, uniqueSlug("rc-get"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/reputation/" + client.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(client.getId()))
                .andExpect(jsonPath("$.strikeCount").value(0))
                .andExpect(jsonPath("$.isBlocked").value(false));
    }

    @Test
    void resetStrikesReturnsZeroedCount() throws Exception {
        User owner = persistUser("reset-owner");
        User client = persistUser("reset-client");
        Business business = seedBusiness(owner, uniqueSlug("rc-reset"));
        seedReputation(client, business, 2, false);

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/reputation/" + client.getId() + "/reset")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strikeCount").value(0));
    }

    @Test
    void unblockClientReturnsUnblocked() throws Exception {
        User owner = persistUser("unblock-owner");
        User client = persistUser("unblock-client");
        Business business = seedBusiness(owner, uniqueSlug("rc-unblock"));
        seedReputation(client, business, 3, true);

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/reputation/" + client.getId() + "/unblock")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(false))
                .andExpect(jsonPath("$.strikeCount").value(0));
    }

    @Test
    void getReputationsByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("rc-perm"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/reputation")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }
}

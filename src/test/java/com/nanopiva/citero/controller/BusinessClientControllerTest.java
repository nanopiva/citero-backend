package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessClient;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessClientRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BusinessClientControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessClientRepository businessClientRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("bc-" + tag + "-" + System.nanoTime() + "@test.com")
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
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    private BusinessClient seedBlock(User client, Business business, boolean blocked) {
        return businessClientRepository.save(BusinessClient.builder()
                .client(client).business(business).isBlocked(blocked).build());
    }

    @Test
    void getBusinessClientsReturnsBusinessList() throws Exception {
        User owner = persistUser("list-owner");
        User client = persistUser("list-client");
        Business business = seedBusiness(owner, uniqueSlug("bc-list"));
        seedBlock(client, business, true);

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/clients")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].clientEmail").value(client.getEmail()));
    }

    @Test
    void getBusinessClientCreatesItWhenMissing() throws Exception {
        User owner = persistUser("get-owner");
        User client = persistUser("get-client");
        Business business = seedBusiness(owner, uniqueSlug("bc-get"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/clients/" + client.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(client.getId()))
                .andExpect(jsonPath("$.isBlocked").value(false));
    }

    @Test
    void blockClientReturnsBlockedWithReason() throws Exception {
        User owner = persistUser("block-owner");
        User client = persistUser("block-client");
        Business business = seedBusiness(owner, uniqueSlug("bc-block"));

        mockMvc.perform(post("/api/businesses/" + business.getId() + "/clients/" + client.getId() + "/block")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Actitud inapropiada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(true))
                .andExpect(jsonPath("$.blockReason").value("Actitud inapropiada"));
    }

    @Test
    void unblockClientReturnsUnblocked() throws Exception {
        User owner = persistUser("unblock-owner");
        User client = persistUser("unblock-client");
        Business business = seedBusiness(owner, uniqueSlug("bc-unblock"));
        seedBlock(client, business, true);

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/clients/" + client.getId() + "/unblock")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(false));
    }

    @Test
    void getBusinessClientsByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("bc-perm"));

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/clients")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }
}

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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BusinessControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;

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
    void createBusinessReturns201AndIsPubliclyReadable() throws Exception {
        User owner = persistUser("create");
        String slug = uniqueSlug("bc-create");

        mockMvc.perform(post("/api/businesses")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Barbería\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.config.reservationMode").value("PUBLIC"));

        mockMvc.perform(get("/api/businesses/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug));
    }

    @Test
    void createBusinessWithDuplicateSlugReturns409() throws Exception {
        User owner = persistUser("dup");
        String slug = uniqueSlug("bc-dup");
        String body = "{\"name\":\"Barbería\",\"slug\":\"" + slug + "\"}";

        mockMvc.perform(post("/api/businesses")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/businesses")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createBusinessWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anónimo\",\"slug\":\"" + uniqueSlug("bc-anon") + "\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateBusinessByOwnerReturnsUpdatedData() throws Exception {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("bc-update"));

        mockMvc.perform(put("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre nuevo\",\"phone\":\"555-0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre nuevo"))
                .andExpect(jsonPath("$.phone").value("555-0000"));
    }

    @Test
    void updateBusinessByNonOwnerReturns403() throws Exception {
        User owner = persistUser("update-owner");
        User intruder = persistUser("update-intruder");
        Business business = seedBusiness(owner, uniqueSlug("bc-update-perm"));

        mockMvc.perform(put("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hackeado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBusinessByOwnerReturns204() throws Exception {
        User owner = persistUser("delete");
        Business business = seedBusiness(owner, uniqueSlug("bc-delete"));

        mockMvc.perform(delete("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/businesses/" + business.getSlug()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBusinessByNonOwnerReturns403() throws Exception {
        User owner = persistUser("delete-owner");
        User intruder = persistUser("delete-intruder");
        Business business = seedBusiness(owner, uniqueSlug("bc-delete-perm"));

        mockMvc.perform(delete("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listBusinessesFiltersByName() throws Exception {
        User owner = persistUser("search");
        String token = "bctoken" + System.nanoTime();
        seedBusinessWithName(owner, uniqueSlug("bc-search-1"), "Negocio " + token + " Uno");
        seedBusinessWithName(owner, uniqueSlug("bc-search-2"), "Negocio " + token + " Dos");

        mockMvc.perform(get("/api/businesses")
                        .param("name", token)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getMyBusinessesReturnsOwnedBusinesses() throws Exception {
        User owner = persistUser("mine");
        seedBusiness(owner, uniqueSlug("bc-mine-1"));
        seedBusiness(owner, uniqueSlug("bc-mine-2"));

        mockMvc.perform(get("/api/businesses/my-businesses")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getMyBusinessesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/businesses/my-businesses"))
                .andExpect(status().is4xxClientError());
    }

    private void seedBusinessWithName(User owner, String slug, String name) {
        Business business = Business.builder().owner(owner).name(name).slug(slug).build();
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
        businessRepository.save(business);
    }
}

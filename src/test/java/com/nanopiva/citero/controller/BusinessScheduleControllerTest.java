package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
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
class BusinessScheduleControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleRepository scheduleRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("bscc-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private void seedSchedule(Business business, BusinessSchedule.DayOfWeek day) {
        scheduleRepository.save(BusinessSchedule.builder()
                .business(business)
                .dayOfWeek(day)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .isClosed(false)
                .build());
    }

    @Test
    void getScheduleReturnsPersistedSchedules() throws Exception {
        User owner = persistUser("get");
        Business business = seedBusiness(owner, uniqueSlug("bscc-get"));
        seedSchedule(business, BusinessSchedule.DayOfWeek.MONDAY);

        mockMvc.perform(get("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));
    }

    @Test
    void updateScheduleReplacesWeeklySchedule() throws Exception {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("bscc-update"));
        seedSchedule(business, BusinessSchedule.DayOfWeek.MONDAY);

        String body = "["
                + "{\"dayOfWeek\":\"MONDAY\",\"openTime\":\"08:00:00\",\"closeTime\":\"17:00:00\",\"isClosed\":false},"
                + "{\"dayOfWeek\":\"TUESDAY\",\"openTime\":\"08:00:00\",\"closeTime\":\"17:00:00\",\"isClosed\":false}"
                + "]";

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateScheduleWithInvalidDayReturns400() throws Exception {
        User owner = persistUser("bad-day");
        Business business = seedBusiness(owner, uniqueSlug("bscc-bad-day"));

        String body = "[{\"dayOfWeek\":\"FUNDAY\",\"openTime\":\"08:00:00\",\"closeTime\":\"17:00:00\",\"isClosed\":false}]";

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateScheduleWithMissingRequiredFieldReturns400() throws Exception {
        User owner = persistUser("bad-field");
        Business business = seedBusiness(owner, uniqueSlug("bscc-bad-field"));

        String body = "[{\"dayOfWeek\":\"MONDAY\",\"isClosed\":false}]";

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateScheduleByNonOwnerReturns403() throws Exception {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("bscc-perm"));

        String body = "[{\"dayOfWeek\":\"MONDAY\",\"openTime\":\"08:00:00\",\"closeTime\":\"17:00:00\",\"isClosed\":false}]";

        mockMvc.perform(put("/api/businesses/" + business.getId() + "/schedules")
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}

package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AvailabilityControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;

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

    private void newStaff(Business business, com.nanopiva.citero.entity.Service service) {
        staffRepository.save(Staff.builder()
                .business(business)
                .customName("Ana")
                .services(new HashSet<>(Set.of(service)))
                .build());
    }

    @Test
    void devuelveSlotsDisponibles() throws Exception {
        Business business = newBusiness("avail-ctrl");
        com.nanopiva.citero.entity.Service service = newService(business);
        newStaff(business, service);
        LocalDate date = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/availability")
                        .param("businessId", business.getId().toString())
                        .param("serviceId", service.getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.availableSlots").isArray())
                .andExpect(jsonPath("$.availableSlots").isNotEmpty());
    }

    @Test
    void negocioInexistenteDevuelveNotFound() throws Exception {
        Business business = newBusiness("avail-404");
        com.nanopiva.citero.entity.Service service = newService(business);
        LocalDate date = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/availability")
                        .param("businessId", "999999")
                        .param("serviceId", service.getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isNotFound());
    }
}

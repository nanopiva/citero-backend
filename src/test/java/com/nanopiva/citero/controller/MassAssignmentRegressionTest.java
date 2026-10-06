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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mass assignment: el backend sólo bindea DTOs con campos explícitos y, con
 * {@code fail-on-unknown-properties=true}, rechaza (400) cualquier propiedad no mapeada.
 * Así no se pueden setear por body campos sensibles (ownership, rol, estado, flags).
 */
@AutoConfigureMockMvc
class MassAssignmentRegressionTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("mass-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
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

    @Test
    void crearNegocioRechazaOwnerIdEnElBody() throws Exception {
        User owner = persistUser("biz");

        mockMvc.perform(post("/api/businesses")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Barbería\",\"slug\":\"mass-" + System.nanoTime()
                                + "\",\"ownerId\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registroRechazaCamposSensiblesEnElBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mass@test.com\",\"password\":\"secret123\","
                                + "\"otpCode\":\"000000\",\"emailVerified\":true,\"isGuest\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarNegocioRechazaSlugNoEditable() throws Exception {
        User owner = persistUser("slug");
        Business business = seedBusiness(owner, "mass-slug-" + System.nanoTime());

        mockMvc.perform(put("/api/businesses/" + business.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"otro-slug\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarPerfilRechazaEmailNoEditable() throws Exception {
        User user = persistUser("mail");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"otro@test.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearTurnoRechazaEstadoYClienteEnElBody() throws Exception {
        // La deserialización falla por propiedades desconocidas antes de tocar el servicio.
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":1,\"startTime\":\"2030-01-01T10:00:00\","
                                + "\"status\":\"COMPLETED\",\"clientId\":999}"))
                .andExpect(status().isBadRequest());
    }
}

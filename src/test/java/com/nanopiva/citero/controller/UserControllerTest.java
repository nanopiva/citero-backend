package com.nanopiva.citero.controller;

import com.nanopiva.citero.entity.Business;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserControllerTest extends IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    private User createUser(String tag) {
        return userRepository.save(User.builder()
                .email(uniqueEmail(tag))
                .password(passwordEncoder.encode("secret123"))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));
    }

    @Test
    void getMeDevuelveElUsuarioAutenticado() throws Exception {
        User user = createUser("me");

        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void getMyWorkspacesIncluyeNegociosPropios() throws Exception {
        User owner = createUser("workspace");
        Business business = businessRepository.save(Business.builder()
                .owner(owner)
                .name("Barbería Workspace")
                .slug("workspace-" + System.nanoTime())
                .build());

        mockMvc.perform(get("/api/users/me/workspaces").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessId").value(business.getId()))
                .andExpect(jsonPath("$[0].businessName").value("Barbería Workspace"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void updateMeActualizaElTelefono() throws Exception {
        User user = createUser("update");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+5491122334455\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+5491122334455"));
    }

    @Test
    void deleteMeEliminaLaCuenta() throws Exception {
        User user = createUser("delete");

        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(user.getId()).isEmpty(), "La cuenta debe eliminarse");
    }

    @Test
    void getMeSinTokenNoDevuelve200() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getUsuarioPropioDevuelve200() throws Exception {
        User user = createUser("self");

        mockMvc.perform(get("/api/users/" + user.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    void getUsuarioDeOtroUsuarioDevuelve403() throws Exception {
        User attacker = createUser("attacker");
        User victim = createUser("victim");

        mockMvc.perform(get("/api/users/" + victim.getId()).header("Authorization", bearer(attacker)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsuarioInexistenteNoRevelaSuExistencia() throws Exception {
        User user = createUser("notfound");

        mockMvc.perform(get("/api/users/999999").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }
}

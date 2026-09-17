package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.dto.user.UserUpdateDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class UserServiceTest extends IntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Evita llamadas reales a Resend al registrar (el registro dispara un OTP de verificación).
    @MockitoBean private EmailService emailService;

    private String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@test.com";
    }

    private String uniqueSlug(String tag) {
        return tag + "-" + System.nanoTime();
    }

    private User persistUser(String email, String rawPassword) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .build());
    }

    @Test
    void registerConEmailDuplicadoLanzaDuplicateResource() {
        String email = uniqueEmail("dup");
        persistUser(email, "secret123");

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email(email)
                .password("otra123")
                .build();

        assertThrows(DuplicateResourceException.class, () -> userService.register(dto),
                "No debe permitirse registrar un email ya existente");
    }

    @Test
    void getByIdInexistenteLanzaResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999_999L));
    }

    @Test
    void updateUserActualizaTelefonoYContrasena() {
        User user = persistUser(uniqueEmail("update"), "vieja123");

        UserResponseDto response = userService.updateUser(user.getId(), UserUpdateDto.builder()
                .phone("+5491122334455")
                .password("nueva123")
                .build());

        assertEquals("+5491122334455", response.getPhone(), "El teléfono debe actualizarse");
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("nueva123", reloaded.getPassword()),
                "La contraseña debe quedar hasheada con el nuevo valor");
    }

    @Test
    void updateUserConCamposNulosNoPisaLosValores() {
        User user = persistUser(uniqueEmail("update-null"), "vieja123");
        user.setPhone("111");
        userRepository.save(user);

        userService.updateUser(user.getId(), UserUpdateDto.builder().build());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("111", reloaded.getPhone(), "Un DTO vacío no debe borrar el teléfono");
        assertTrue(passwordEncoder.matches("vieja123", reloaded.getPassword()),
                "Un DTO vacío no debe cambiar la contraseña");
    }

    @Test
    void deleteAccountEliminaUsuarioNegocioYStaff() {
        User user = persistUser(uniqueEmail("delete"), "secret123");
        Business business = businessRepository.save(Business.builder()
                .owner(user)
                .name("Barbería Delete")
                .slug(uniqueSlug("delete"))
                .build());
        Staff staff = staffRepository.save(Staff.builder()
                .business(business)
                .user(user)
                .build());

        userService.deleteAccount(user.getId());

        assertTrue(userRepository.findById(user.getId()).isEmpty(), "El usuario debe eliminarse");
        assertTrue(businessRepository.findById(business.getId()).isEmpty(), "El negocio propio debe eliminarse");
        assertTrue(staffRepository.findById(staff.getId()).isEmpty(), "El perfil de staff debe eliminarse");
    }

    @Test
    void findOrCreateGuestUserEsIdempotentePorEmail() {
        String email = uniqueEmail("guest");

        User first = userService.findOrCreateGuestUser(email, "555");
        User second = userService.findOrCreateGuestUser(email, "555");

        assertEquals(first.getId(), second.getId(), "Un mismo email debe reutilizar el usuario existente");
        assertEquals(email, second.getEmail());
        assertNotNull(second.getPassword(), "El usuario invitado debe tener contraseña generada");
    }

    @Test
    void registerConTokenDeInvitacionVinculaElStaff() {
        String email = uniqueEmail("invite");
        Staff orphan = orphanStaff(email, LocalDateTime.now().plusDays(7));

        userService.register(RegisterRequestDto.builder()
                .email(email)
                .password("secret123")
                .invitationToken(orphan.getInvitationToken())
                .build());

        Staff linked = staffRepository.findById(orphan.getId()).orElseThrow();
        assertNotNull(linked.getUser(), "Con un token válido el staff debe quedar vinculado");
        assertEquals(email, linked.getUser().getEmail());
        assertNull(linked.getContactEmail(), "El email de contacto se limpia al vincular");
        assertTrue(linked.getUser().getEmailVerified(), "La invitación verifica el email");
    }

    @Test
    void registerConTokenVencidoNoVincula() {
        String email = uniqueEmail("invite-expired");
        Staff orphan = orphanStaff(email, LocalDateTime.now().minusDays(1));

        userService.register(RegisterRequestDto.builder()
                .email(email)
                .password("secret123")
                .invitationToken(orphan.getInvitationToken())
                .build());

        assertNull(staffRepository.findById(orphan.getId()).orElseThrow().getUser(),
                "Un token vencido no debe vincular");
    }

    private Staff orphanStaff(String email, LocalDateTime expiresAt) {
        User owner = persistUser(uniqueEmail("owner-invite"), "secret123");
        Business business = businessRepository.save(Business.builder()
                .owner(owner)
                .name("Barbería Invitación")
                .slug(uniqueSlug("invite"))
                .build());
        return staffRepository.save(Staff.builder()
                .business(business)
                .contactEmail(email)
                .customName("Juan")
                .invitationToken("token-" + System.nanoTime())
                .invitationExpiresAt(expiresAt)
                .build());
    }

    @Test
    void registerReclamaCuentaDeInvitadoConservandoElUsuario() {
        String email = uniqueEmail("guest-claim");
        User guest = userService.findOrCreateGuestUser(email, "555");
        Long guestId = guest.getId();

        UserResponseDto response = userService.register(RegisterRequestDto.builder()
                .email(email)
                .password("secret123")
                .phone("111")
                .build());

        assertEquals(guestId, response.getId(),
                "Debe reclamar la cuenta de invitado existente, no crear otra");
        User reloaded = userRepository.findById(guestId).orElseThrow();
        assertFalse(Boolean.TRUE.equals(reloaded.getIsGuest()),
                "La cuenta debe dejar de ser de invitado");
        assertTrue(passwordEncoder.matches("secret123", reloaded.getPassword()),
                "La contraseña debe ser la que eligió al registrarse");
    }
}

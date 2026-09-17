package com.nanopiva.citero.dto;

import com.nanopiva.citero.dto.appointment.AppointmentCreateRequestDto;
import com.nanopiva.citero.dto.business.BusinessConfigRequestDto;
import com.nanopiva.citero.dto.business.BusinessCreateRequestDto;
import com.nanopiva.citero.dto.business.ServiceCreateRequestDto;
import com.nanopiva.citero.dto.business.StaffCreateRequestDto;
import com.nanopiva.citero.dto.user.LoginRequestDto;
import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.ResetPasswordRequestDto;
import com.nanopiva.citero.support.IntegrationTest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida las constraints de Jakarta de los DTOs de entrada usando el
 * {@link Validator} real del contexto (no un mock).
 */
class DtoValidationTest extends IntegrationTest {

    @Autowired
    private Validator validator;

    private Map<String, String> violations(Object dto) {
        return validator.validate(dto).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                v -> v.getMessage(),
                (a, b) -> a));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@test.com";
    }

    // ------------------------------------------------------------------
    // RegisterRequestDto
    // ------------------------------------------------------------------

    @Test
    void registerValidoNoTieneViolaciones() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email(uniqueEmail("register"))
                .password("secret123")
                .build();

        assertTrue(violations(dto).isEmpty(), "Un registro válido no debe tener violaciones");
    }

    @Test
    void registerConEmailInvalidoYPasswordCortaEsRechazado() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("no-es-un-email")
                .password("123")
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El formato del email no es válido", violations.get("email"));
        assertEquals("La contraseña debe tener entre 6 y 100 caracteres", violations.get("password"));
    }

    @Test
    void registerConCamposVaciosEsRechazado() {
        Map<String, String> violations = violations(RegisterRequestDto.builder().build());

        assertEquals("El email es obligatorio", violations.get("email"));
        assertEquals("La contraseña es obligatoria", violations.get("password"));
    }

    // ------------------------------------------------------------------
    // LoginRequestDto
    // ------------------------------------------------------------------

    @Test
    void loginValidoNoTieneViolaciones() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email(uniqueEmail("login"))
                .password("secret123")
                .build();

        assertTrue(violations(dto).isEmpty(), "Un login válido no debe tener violaciones");
    }

    @Test
    void loginConEmailInvalidoYPasswordVaciaEsRechazado() {
        LoginRequestDto dto = LoginRequestDto.builder()
                .email("sin-arroba")
                .password("")
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El formato del email no es válido", violations.get("email"));
        assertEquals("La contraseña es obligatoria", violations.get("password"));
    }

    // ------------------------------------------------------------------
    // BusinessCreateRequestDto
    // ------------------------------------------------------------------

    @Test
    void businessCreateValidoNoTieneViolaciones() {
        BusinessCreateRequestDto dto = BusinessCreateRequestDto.builder()
                .name("Barbería Central")
                .slug("barberia-central")
                .build();

        assertTrue(violations(dto).isEmpty(), "Un negocio válido no debe tener violaciones");
    }

    @Test
    void businessCreateConNombreVacioYSlugInvalidoEsRechazado() {
        BusinessCreateRequestDto dto = BusinessCreateRequestDto.builder()
                .name(" ")
                .slug("Slug Inválido!")
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El nombre del negocio es obligatorio", violations.get("name"));
        assertEquals("El slug solo puede contener letras minúsculas, números y guiones",
                violations.get("slug"));
    }

    // ------------------------------------------------------------------
    // ServiceCreateRequestDto
    // ------------------------------------------------------------------

    @Test
    void serviceCreateValidoNoTieneViolaciones() {
        ServiceCreateRequestDto dto = ServiceCreateRequestDto.builder()
                .name("Corte de pelo")
                .durationMinutes(30)
                .price(BigDecimal.TEN)
                .build();

        assertTrue(violations(dto).isEmpty(), "Un servicio válido no debe tener violaciones");
    }

    @Test
    void serviceCreateConDuracionFueraDeRangoEsRechazado() {
        ServiceCreateRequestDto demasiadoCorto = ServiceCreateRequestDto.builder()
                .name("Corte")
                .durationMinutes(4)
                .price(BigDecimal.TEN)
                .build();
        ServiceCreateRequestDto demasiadoLargo = ServiceCreateRequestDto.builder()
                .name("Corte")
                .durationMinutes(481)
                .price(BigDecimal.TEN)
                .build();

        assertEquals("La duración mínima es de 5 minutos",
                violations(demasiadoCorto).get("durationMinutes"));
        assertEquals("La duración máxima es de 480 minutos (8 horas)",
                violations(demasiadoLargo).get("durationMinutes"));
    }

    @Test
    void serviceCreateConPrecioNegativoYNombreVacioEsRechazado() {
        ServiceCreateRequestDto dto = ServiceCreateRequestDto.builder()
                .name("")
                .durationMinutes(30)
                .price(new BigDecimal("-0.01"))
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El nombre del servicio es obligatorio", violations.get("name"));
        assertEquals("El precio no puede ser negativo", violations.get("price"));
    }

    @Test
    void serviceCreateConCamposNulosEsRechazado() {
        Map<String, String> violations = violations(ServiceCreateRequestDto.builder().build());

        assertEquals("El nombre del servicio es obligatorio", violations.get("name"));
        assertEquals("La duración es obligatoria", violations.get("durationMinutes"));
        assertEquals("El precio es obligatorio", violations.get("price"));
    }

    // ------------------------------------------------------------------
    // BusinessConfigRequestDto
    // ------------------------------------------------------------------

    @Test
    void businessConfigValidaNoTieneViolaciones() {
        BusinessConfigRequestDto dto = BusinessConfigRequestDto.builder()
                .reservationMode("PUBLIC")
                .cancellationToleranceHours(24)
                .enablePenalties(true)
                .maxStrikes(3)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();

        assertTrue(violations(dto).isEmpty(), "Una config válida no debe tener violaciones");
    }

    @Test
    void businessConfigConTodosLosCamposNulosEsRechazada() {
        Map<String, String> violations = violations(BusinessConfigRequestDto.builder().build());

        assertTrue(violations.containsKey("reservationMode"), "reservationMode es obligatorio");
        assertTrue(violations.containsKey("cancellationToleranceHours"), "Las horas de tolerancia son obligatorias");
        assertTrue(violations.containsKey("enablePenalties"), "enablePenalties es obligatorio");
        assertTrue(violations.containsKey("maxStrikes"), "maxStrikes es obligatorio");
        assertTrue(violations.containsKey("defaultOpeningTime"), "defaultOpeningTime es obligatorio");
        assertTrue(violations.containsKey("defaultClosingTime"), "defaultClosingTime es obligatorio");
        assertTrue(violations.containsKey("enableReminders"), "enableReminders es obligatorio");
        assertTrue(violations.containsKey("reminder24hEnabled"), "reminder24hEnabled es obligatorio");
        assertTrue(violations.containsKey("reminder2hEnabled"), "reminder2hEnabled es obligatorio");
    }

    @Test
    void businessConfigConRangosInvalidosEsRechazada() {
        BusinessConfigRequestDto dto = BusinessConfigRequestDto.builder()
                .reservationMode("PUBLIC")
                .cancellationToleranceHours(169)
                .enablePenalties(true)
                .maxStrikes(0)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("Las horas de tolerancia no pueden superar 168 (1 semana)",
                violations.get("cancellationToleranceHours"));
        assertEquals("El máximo de strikes debe ser al menos 1", violations.get("maxStrikes"));
    }

    // ------------------------------------------------------------------
    // StaffCreateRequestDto
    // ------------------------------------------------------------------

    @Test
    void staffCreateValidoNoTieneViolaciones() {
        StaffCreateRequestDto dto = StaffCreateRequestDto.builder()
                .email(uniqueEmail("staff"))
                .customName("Ana")
                .build();

        assertTrue(violations(dto).isEmpty(), "Un staff válido no debe tener violaciones");
    }

    @Test
    void staffCreateConEmailInvalidoYNombreLargoEsRechazado() {
        StaffCreateRequestDto dto = StaffCreateRequestDto.builder()
                .email("correo-malo")
                .customName("x".repeat(101))
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El formato del email no es válido", violations.get("email"));
        assertEquals("El nombre personalizado no puede tener más de 100 caracteres",
                violations.get("customName"));
    }

    // ------------------------------------------------------------------
    // AppointmentCreateRequestDto
    // ------------------------------------------------------------------

    @Test
    void appointmentCreateValidoNoTieneViolaciones() {
        AppointmentCreateRequestDto dto = AppointmentCreateRequestDto.builder()
                .serviceId(1L)
                .startTime(LocalDateTime.now().plusDays(1))
                .build();

        assertTrue(violations(dto).isEmpty(), "Un turno válido no debe tener violaciones");
    }

    @Test
    void appointmentCreateSinServicioNiFechaEsRechazado() {
        Map<String, String> violations = violations(AppointmentCreateRequestDto.builder().build());

        assertEquals("El ID del servicio es obligatorio", violations.get("serviceId"));
        assertEquals("La fecha y hora de inicio es obligatoria", violations.get("startTime"));
    }

    @Test
    void appointmentCreateConGuestEmailInvalidoEsRechazado() {
        AppointmentCreateRequestDto dto = AppointmentCreateRequestDto.builder()
                .serviceId(1L)
                .startTime(LocalDateTime.now().plusDays(1))
                .guestEmail("esto-no-es-un-email")
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El formato del email no es válido", violations.get("guestEmail"));
        assertEquals(1, violations.size(), "El resto del DTO es válido");
    }

    // ------------------------------------------------------------------
    // ResetPasswordRequestDto
    // ------------------------------------------------------------------

    @Test
    void resetPasswordValidoNoTieneViolaciones() {
        ResetPasswordRequestDto dto = ResetPasswordRequestDto.builder()
                .email(uniqueEmail("reset"))
                .otpCode("123456")
                .newPassword("secret123")
                .build();

        assertTrue(violations(dto).isEmpty(), "Un reset válido no debe tener violaciones");
    }

    @Test
    void resetPasswordConDatosInvalidosEsRechazado() {
        ResetPasswordRequestDto dto = ResetPasswordRequestDto.builder()
                .email("correo-malo")
                .otpCode("123")
                .newPassword("123")
                .build();

        Map<String, String> violations = violations(dto);

        assertEquals("El formato del email no es válido.", violations.get("email"));
        assertEquals("El código OTP debe tener entre 4 y 6 dígitos.", violations.get("otpCode"));
        assertEquals("La contraseña debe tener entre 6 y 100 caracteres.", violations.get("newPassword"));
    }

    @Test
    void resetPasswordConCamposVaciosEsRechazado() {
        Map<String, String> violations = violations(ResetPasswordRequestDto.builder().build());

        assertEquals("El email es obligatorio.", violations.get("email"));
        assertEquals("El código OTP es obligatorio.", violations.get("otpCode"));
        assertEquals("La nueva contraseña es obligatoria.", violations.get("newPassword"));
    }
}

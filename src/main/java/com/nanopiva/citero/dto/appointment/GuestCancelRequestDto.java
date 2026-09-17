package com.nanopiva.citero.dto.appointment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para la cancelación de un turno por parte de un invitado (sin login).
 * Requiere el email del cliente y el código OTP de verificación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestCancelRequestDto {

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "El formato del email no es válido.")
    private String email;

    @NotBlank(message = "El código de verificación es obligatorio.")
    private String otpCode;
}
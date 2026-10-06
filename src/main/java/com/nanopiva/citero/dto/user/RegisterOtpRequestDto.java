package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud del OTP de registro. Incluye la contraseña para poder validar su política
 * (común/filtrada) ANTES de emitir el código, evitando consumir un OTP con una inválida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterOtpRequestDto {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;
}

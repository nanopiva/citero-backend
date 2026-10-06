package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Segundo paso del login: el token de desafío devuelto por /login + el código (TOTP o recuperación). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerifyRequestDto {

    @NotBlank(message = "El token de MFA es obligatorio")
    private String mfaToken;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código no puede tener más de 20 caracteres")
    private String code;
}

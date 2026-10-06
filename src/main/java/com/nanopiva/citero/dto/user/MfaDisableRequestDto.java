package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Desactivar MFA exige reautenticación: contraseña actual + un código válido (TOTP o recuperación). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaDisableRequestDto {

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 72, message = "La contraseña no puede tener más de 72 caracteres")
    private String password;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código no puede tener más de 20 caracteres")
    private String code;
}

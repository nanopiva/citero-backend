package com.nanopiva.citero.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del login. Si la cuenta tiene MFA activo, {@code mfaRequired=true} y se devuelve
 * {@code mfaToken} para completar el segundo factor (sin access token todavía).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private UserResponseDto user;

    @Builder.Default
    private boolean mfaRequired = false;

    private String mfaToken;
}

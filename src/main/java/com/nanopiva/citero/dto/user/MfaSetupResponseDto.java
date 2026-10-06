package com.nanopiva.citero.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos de enrolamiento TOTP (secreto + URI otpauth para la app de autenticación). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponseDto {
    private String secret;
    private String otpauthUri;
}

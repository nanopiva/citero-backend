package com.nanopiva.citero.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Códigos de recuperación (se muestran una única vez al activar el MFA). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaEnableResponseDto {
    private List<String> recoveryCodes;
}

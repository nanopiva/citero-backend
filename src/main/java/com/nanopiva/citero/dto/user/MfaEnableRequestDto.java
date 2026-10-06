package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaEnableRequestDto {

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6,8}", message = "El código debe tener entre 6 y 8 dígitos")
    private String code;
}

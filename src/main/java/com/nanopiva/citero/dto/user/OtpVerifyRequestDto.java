package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyRequestDto {

    @NotBlank(message = "El target es obligatorio")
    private String target;

    @NotBlank(message = "El código es obligatorio")
    @Size(min = 4, max = 6, message = "El código debe tener entre 4 y 6 dígitos")
    private String code;
}
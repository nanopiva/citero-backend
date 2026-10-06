package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBlockRequestDto {

    @Size(max = 255, message = "El motivo no puede tener más de 255 caracteres")
    private String reason;
}

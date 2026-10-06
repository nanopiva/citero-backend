package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceUpdateDto {

    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;

    @Min(value = 5, message = "La duración mínima es de 5 minutos")
    @Max(value = 480, message = "La duración máxima es de 480 minutos")
    private Integer durationMinutes;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    private BigDecimal price;
}
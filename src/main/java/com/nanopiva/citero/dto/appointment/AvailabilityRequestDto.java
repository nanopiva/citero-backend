package com.nanopiva.citero.dto.appointment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequestDto {

    @NotNull(message = "El ID del servicio es obligatorio")
    private Long serviceId;

    private Long staffId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate date;
}
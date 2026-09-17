package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessScheduleRequestDto {

    @NotBlank(message = "El día de la semana es obligatorio")
    private String dayOfWeek; // "MONDAY", "TUESDAY", etc.

    @NotNull(message = "La hora de apertura es obligatoria")
    private LocalTime openTime;

    @NotNull(message = "La hora de cierre es obligatoria")
    private LocalTime closeTime;

    @NotNull(message = "El campo 'isClosed' es obligatorio")
    private Boolean isClosed;
}
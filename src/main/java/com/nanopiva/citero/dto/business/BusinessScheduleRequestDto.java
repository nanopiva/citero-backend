package com.nanopiva.citero.dto.business;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessScheduleRequestDto {

    @NotBlank(message = "El día de la semana es obligatorio")
    private String dayOfWeek; // "MONDAY", "TUESDAY", etc.

    @NotNull(message = "El campo 'isClosed' es obligatorio")
    private Boolean isClosed;

    // Franjas del día. Obligatorio al menos una si el día no está cerrado.
    private List<@Valid SchedulePeriodDto> periods;
}

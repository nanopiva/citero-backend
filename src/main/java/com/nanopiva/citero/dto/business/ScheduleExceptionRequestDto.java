package com.nanopiva.citero.dto.business;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleExceptionRequestDto {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate date;

    @NotNull(message = "El campo 'isClosed' es obligatorio")
    private Boolean isClosed;

    // Si no está cerrado, debe traer al menos una franja.
    private List<@Valid SchedulePeriodDto> periods;
}

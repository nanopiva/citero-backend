package com.nanopiva.citero.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Horario efectivo de una fecha concreta: ya resuelve la excepción puntual, la
 * regla semanal o el valor por defecto de la configuración.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveScheduleResponseDto {

    private LocalDate date;
    private Boolean isClosed;
    private List<SchedulePeriodDto> periods;
}

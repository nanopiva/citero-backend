package com.nanopiva.citero.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfigResponseDto {
    private String reservationMode;
    private Integer cancellationToleranceHours;
    private Boolean enablePenalties;
    private Integer maxStrikes;
    private LocalTime defaultOpeningTime;
    private LocalTime defaultClosingTime;

    // Campos de configuración de recordatorios
    private Boolean enableReminders;
    private Boolean reminder24hEnabled;
    private Boolean reminder2hEnabled;
}
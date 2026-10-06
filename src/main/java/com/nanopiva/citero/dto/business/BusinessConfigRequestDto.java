package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

/**
 * Configuración de reserva con semántica PATCH: cada campo es opcional y, si viene
 * null, no se modifica. Los rangos se validan sólo cuando el campo está presente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfigRequestDto {

    private String reservationMode; // Valores válidos: "PUBLIC" o "AUTHENTICATED"

    @Min(value = 0, message = "Las horas de tolerancia no pueden ser negativas")
    @Max(value = 168, message = "Las horas de tolerancia no pueden superar 168 (1 semana)")
    private Integer cancellationToleranceHours;

    private Boolean staffCanViewFullAgenda;

    private LocalTime defaultOpeningTime;

    private LocalTime defaultClosingTime;

    private Boolean enableReminders;

    private Boolean reminder24hEnabled;

    private Boolean reminder2hEnabled;
}

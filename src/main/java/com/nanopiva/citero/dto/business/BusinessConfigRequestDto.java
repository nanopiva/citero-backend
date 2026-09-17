package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfigRequestDto {
    @NotBlank(message = "El modo de reserva es obligatorio")
    private String reservationMode; // Valores válidos: "PUBLIC" o "AUTHENTICATED"

    @NotNull(message = "Las horas de tolerancia son obligatorias")
    @Min(value = 0, message = "Las horas de tolerancia no pueden ser negativas")
    @Max(value = 168, message = "Las horas de tolerancia no pueden superar 168 (1 semana)")
    private Integer cancellationToleranceHours;

    @NotNull(message = "El campo de habilitar sanciones es obligatorio")
    private Boolean enablePenalties;

    @NotNull(message = "El máximo de strikes es obligatorio")
    @Min(value = 1, message = "El máximo de strikes debe ser al menos 1")
    @Max(value = 10, message = "El máximo de strikes no puede superar 10")
    private Integer maxStrikes;

    @NotNull(message = "La hora de apertura es obligatoria")
    private LocalTime defaultOpeningTime;

    @NotNull(message = "La hora de cierre es obligatoria")
    private LocalTime defaultClosingTime;

    // Campos de configuración de recordatorios
    @NotNull(message = "El campo de habilitar recordatorios es obligatorio")
    private Boolean enableReminders;

    @NotNull(message = "El campo de habilitar recordatorio 24h es obligatorio")
    private Boolean reminder24hEnabled;

    @NotNull(message = "El campo de habilitar recordatorio 2h es obligatorio")
    private Boolean reminder2hEnabled;
}
package com.nanopiva.citero.dto.business;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

/** Para anónimos sólo se completa reservationMode; el resto queda null y no se serializa. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessConfigResponseDto {
    private String reservationMode;
    private Integer cancellationToleranceHours;
    private Boolean staffCanViewFullAgenda;
    private LocalTime defaultOpeningTime;
    private LocalTime defaultClosingTime;

    private Boolean enableReminders;
    private Boolean reminder24hEnabled;
    private Boolean reminder2hEnabled;
}
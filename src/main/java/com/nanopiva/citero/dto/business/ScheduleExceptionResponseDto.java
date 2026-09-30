package com.nanopiva.citero.dto.business;

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
public class ScheduleExceptionResponseDto {

    private Long id;
    private LocalDate date;
    private Boolean isClosed;
    private List<SchedulePeriodDto> periods;
}

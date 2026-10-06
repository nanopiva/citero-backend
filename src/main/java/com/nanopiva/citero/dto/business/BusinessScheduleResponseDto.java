package com.nanopiva.citero.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessScheduleResponseDto {

    private Long id;
    private String dayOfWeek;
    private Boolean isClosed;
    private List<SchedulePeriodDto> periods;
}

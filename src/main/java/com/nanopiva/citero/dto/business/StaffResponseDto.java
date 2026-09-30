package com.nanopiva.citero.dto.business;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponseDto {

    private Long id;
    private String customName;

    // Sólo se expone al dueño; para el resto queda null y no se serializa.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String userEmail;

    private Set<ServiceResponseDto> services;
    private boolean hasClaimedAccount;
}
package com.nanopiva.citero.dto.business;

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
    private String userEmail;
    private Set<ServiceResponseDto> services;
    private boolean hasClaimedAccount;
}
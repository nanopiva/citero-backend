package com.nanopiva.citero.dto.appointment;

import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.dto.business.StaffResponseDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponseDto {

    private Long id;
    private String businessName;
    // Zona horaria del negocio (IANA): startTime/endTime son hora local de esa zona.
    private String timezone;
    private UserResponseDto client;
    private StaffResponseDto staff;
    private ServiceResponseDto service;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Boolean cancelledLate;
    private LocalDateTime createdAt;
}
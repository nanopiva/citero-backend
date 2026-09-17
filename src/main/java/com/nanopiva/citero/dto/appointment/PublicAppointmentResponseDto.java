package com.nanopiva.citero.dto.appointment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO ligero para exponer los detalles básicos de un turno en la página pública de gestión.
 * No incluye datos del cliente: el email se pide y se valida por OTP al cancelar, para no
 * permitir enumerar turnos ajenos adivinando IDs secuenciales. {@code ownedByViewer} solo
 * indica si el usuario autenticado (si lo hay) es el cliente del turno.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicAppointmentResponseDto {
    private Long id;
    private String businessName;
    private String serviceName;
    private String staffName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String address;
    private boolean ownedByViewer;
}
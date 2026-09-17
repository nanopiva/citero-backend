package com.nanopiva.citero.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    private Long businessId;
    private String businessName;

    // Métricas de turnos
    private Long appointmentsToday;
    private Long appointmentsThisMonth;
    private Long appointmentsPending;

    // Métricas financieras
    private BigDecimal estimatedRevenueToday;
    private BigDecimal estimatedRevenueThisMonth;

    // Métricas de ocupación
    private Double occupancyRate; // porcentaje 0-100

    // Reputación
    private Long blockedClientsCount;
}
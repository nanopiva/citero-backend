package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.DashboardStatsDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessClientRepository;
import com.nanopiva.citero.util.BusinessTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessClientRepository businessClientRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Long businessId, Long ownerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para ver este dashboard.");
        }

        LocalDate today = BusinessTime.today(business);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay();

        var todayAppointments = appointmentRepository
                .findForBusinessInRange(businessId, startOfDay, endOfDay);

        var monthAppointments = appointmentRepository
                .findForBusinessInRange(businessId, startOfMonth, endOfMonth);

        long appointmentsToday = todayAppointments.size();
        long appointmentsThisMonth = monthAppointments.size();
        long pending = todayAppointments.stream()
                .filter(a -> a.getStatus() == Appointment.AppointmentStatus.CONFIRMED)
                .count();

        BigDecimal revenueToday = todayAppointments.stream()
                .filter(a -> a.getStatus() == Appointment.AppointmentStatus.CONFIRMED ||
                        a.getStatus() == Appointment.AppointmentStatus.COMPLETED)
                .map(a -> a.getService().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenueMonth = monthAppointments.stream()
                .filter(a -> a.getStatus() == Appointment.AppointmentStatus.CONFIRMED ||
                        a.getStatus() == Appointment.AppointmentStatus.COMPLETED)
                .map(a -> a.getService().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long blockedClients = businessClientRepository.countByBusinessAndIsBlockedTrue(business);

        // Ocupación: turnos confirmados hoy / slots disponibles teóricos (simplificado)
        double occupancy = 0.0;
        if (!todayAppointments.isEmpty()) {
            long completedOrConfirmed = todayAppointments.stream()
                    .filter(a -> a.getStatus() == Appointment.AppointmentStatus.CONFIRMED ||
                            a.getStatus() == Appointment.AppointmentStatus.COMPLETED)
                    .count();
            occupancy = (completedOrConfirmed * 100.0) / appointmentsToday;
        }

        return DashboardStatsDto.builder()
                .businessId(business.getId())
                .businessName(business.getName())
                .appointmentsToday(appointmentsToday)
                .appointmentsThisMonth(appointmentsThisMonth)
                .appointmentsPending(pending)
                .estimatedRevenueToday(revenueToday)
                .estimatedRevenueThisMonth(revenueMonth)
                .occupancyRate(Math.round(occupancy * 100.0) / 100.0)
                .blockedClientsCount(blockedClients)
                .build();
    }
}
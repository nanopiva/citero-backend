package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.appointment.AppointmentCreateRequestDto;
import com.nanopiva.citero.dto.appointment.AppointmentResponseDto;
import com.nanopiva.citero.dto.appointment.PublicAppointmentResponseDto;
import com.nanopiva.citero.dto.appointment.GuestCancelRequestDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<AppointmentResponseDto> createAppointment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AppointmentCreateRequestDto requestDto) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.status(201).body(appointmentService.createAppointment(userId, requestDto));
    }

    @GetMapping("/my-appointments")
    public ResponseEntity<Page<AppointmentResponseDto>> getMyAppointments(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByClient(userDetails.getId(), pageable));
    }

    @GetMapping
    public ResponseEntity<Page<AppointmentResponseDto>> getAppointments(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam Long businessId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Appointment.AppointmentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(
                appointmentService.getAppointments(userDetails.getId(), businessId, staffId, date, status, pageable));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponseDto> cancelByClient(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(appointmentService.cancelByClient(id, userDetails.getId()));
    }

    @PutMapping("/{id}/cancel-by-business")
    public ResponseEntity<AppointmentResponseDto> cancelByBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(appointmentService.cancelByBusiness(id, userDetails.getId()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AppointmentResponseDto> updateStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam Appointment.AppointmentStatus newStatus) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, userDetails.getId(), newStatus));
    }

    /**
     * Obtiene los detalles básicos de un turno para mostrar en la página pública de gestión.
     * No requiere autenticación.
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<PublicAppointmentResponseDto> getPublicAppointmentDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long viewerId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(appointmentService.getPublicAppointmentDetails(id, viewerId));
    }

    /**
     * Envía un OTP al email del cliente para autorizar la cancelación de un turno.
     * Valida que el email coincida con el cliente del turno.
     */
    @PostMapping("/public/{id}/send-cancellation-otp")
    public ResponseEntity<Void> sendCancellationOtp(
            @PathVariable Long id,
            @RequestParam String email) {
        appointmentService.sendCancellationOtp(id, email);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cancela un turno validando la identidad del cliente mediante OTP.
     * No requiere autenticación (el OTP actúa como prueba de identidad).
     */
    @PutMapping("/public/{id}/cancel")
    public ResponseEntity<AppointmentResponseDto> cancelByGuest(
            @PathVariable Long id,
            @Valid @RequestBody GuestCancelRequestDto requestDto) {
        return ResponseEntity.ok(appointmentService.cancelByGuest(id, requestDto.getEmail(), requestDto.getOtpCode()));
    }
}
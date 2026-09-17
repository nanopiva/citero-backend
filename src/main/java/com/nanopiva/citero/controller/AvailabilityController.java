package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.appointment.AvailabilityResponseDto;
import com.nanopiva.citero.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<AvailabilityResponseDto> getAvailableSlots(
            @RequestParam Long businessId,
            @RequestParam Long serviceId,
            @RequestParam(required = false) Long staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var availability = availabilityService.getAvailableSlots(businessId, serviceId, staffId, date);
        return ResponseEntity.ok(availability);
    }
}

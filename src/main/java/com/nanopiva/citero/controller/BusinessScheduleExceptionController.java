package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.ScheduleExceptionRequestDto;
import com.nanopiva.citero.dto.business.ScheduleExceptionResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.BusinessScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/schedule-exceptions")
@RequiredArgsConstructor
public class BusinessScheduleExceptionController {

    private final BusinessScheduleService businessScheduleService;

    @GetMapping
    public ResponseEntity<List<ScheduleExceptionResponseDto>> getExceptions(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(businessScheduleService.getExceptions(businessId, userDetails.getId()));
    }

    @PutMapping
    public ResponseEntity<ScheduleExceptionResponseDto> upsertException(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ScheduleExceptionRequestDto requestDto) {
        return ResponseEntity.ok(
                businessScheduleService.upsertException(businessId, userDetails.getId(), requestDto));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteException(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        businessScheduleService.deleteException(businessId, userDetails.getId(), date);
        return ResponseEntity.noContent().build();
    }
}

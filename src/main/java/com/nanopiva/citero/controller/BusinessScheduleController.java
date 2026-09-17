package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.BusinessScheduleRequestDto;
import com.nanopiva.citero.dto.business.BusinessScheduleResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.BusinessScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/schedules")
@RequiredArgsConstructor
public class BusinessScheduleController {

    private final BusinessScheduleService businessScheduleService;

    @GetMapping
    public ResponseEntity<List<BusinessScheduleResponseDto>> getSchedule(@PathVariable Long businessId) {
        return ResponseEntity.ok(businessScheduleService.getScheduleByBusinessId(businessId));
    }

    @PutMapping
    public ResponseEntity<List<BusinessScheduleResponseDto>> updateSchedule(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Validated @RequestBody List<@Valid BusinessScheduleRequestDto> requestDtos) {
        return ResponseEntity.ok(businessScheduleService.updateWeeklySchedule(businessId, userDetails.getId(), requestDtos));
    }
}
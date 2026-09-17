package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.StaffCreateRequestDto;
import com.nanopiva.citero.dto.business.StaffResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/businesses/{businessId}/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    public ResponseEntity<StaffResponseDto> createStaff(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody StaffCreateRequestDto requestDto) {
        return ResponseEntity.status(201).body(staffService.createStaff(businessId, userDetails.getId(), requestDto));
    }

    @PostMapping("/me")
    public ResponseEntity<StaffResponseDto> addSelfAsStaff(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody StaffCreateRequestDto requestDto) {
        return ResponseEntity.status(201)
                .body(staffService.addOwnerAsStaff(businessId, userDetails.getId(), requestDto));
    }

    @GetMapping
    public ResponseEntity<List<StaffResponseDto>> getStaff(@PathVariable Long businessId) {
        return ResponseEntity.ok(staffService.getStaffByBusinessId(businessId));
    }

    @PutMapping("/{staffId}")
    public ResponseEntity<StaffResponseDto> updateStaff(
            @PathVariable Long staffId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody StaffCreateRequestDto requestDto) {
        return ResponseEntity.ok(staffService.updateStaff(staffId, userDetails.getId(), requestDto));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> leaveStaff(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        staffService.leaveStaff(businessId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{staffId}/resend-invitation")
    public ResponseEntity<Void> resendInvitation(
            @PathVariable Long businessId,
            @PathVariable Long staffId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        staffService.resendInvitation(staffId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{staffId}")
    public ResponseEntity<Void> deleteStaff(

            @PathVariable Long staffId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        staffService.deleteStaff(staffId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{staffId}/services")
    public ResponseEntity<StaffResponseDto> assignServices(

            @PathVariable Long staffId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody Set<Long> serviceIds) {
        return ResponseEntity.ok(staffService.assignServicesToStaff(staffId, userDetails.getId(), serviceIds));
    }
}
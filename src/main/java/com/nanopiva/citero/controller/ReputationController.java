package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.ClientReputationResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.ReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/reputation")
@RequiredArgsConstructor
public class ReputationController {

    private final ReputationService reputationService;

    @GetMapping
    public ResponseEntity<Page<ClientReputationResponseDto>> getReputations(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(
                reputationService.getReputationsByBusiness(businessId, userDetails.getId(), pageable));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientReputationResponseDto> getReputation(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(reputationService.getReputationWithValidation(clientId, businessId, userDetails.getId()));
    }

    @PutMapping("/{clientId}/reset")
    public ResponseEntity<ClientReputationResponseDto> resetStrikes(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(reputationService.resetStrikes(clientId, businessId, userDetails.getId()));
    }

    @PutMapping("/{clientId}/unblock")
    public ResponseEntity<ClientReputationResponseDto> unblockClient(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(reputationService.unblockClient(clientId, businessId, userDetails.getId()));
    }
}
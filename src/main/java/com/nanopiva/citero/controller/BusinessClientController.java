package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.BusinessClientResponseDto;
import com.nanopiva.citero.dto.business.ClientBlockRequestDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.BusinessClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/clients")
@RequiredArgsConstructor
public class BusinessClientController {

    private final BusinessClientService businessClientService;

    @GetMapping
    public ResponseEntity<Page<BusinessClientResponseDto>> getBusinessClients(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(
                businessClientService.getBusinessClients(businessId, userDetails.getId(), pageable));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<BusinessClientResponseDto> getBusinessClient(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(businessClientService.getBusinessClient(clientId, businessId, userDetails.getId()));
    }

    @PostMapping("/{clientId}/block")
    public ResponseEntity<BusinessClientResponseDto> blockClient(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ClientBlockRequestDto requestDto) {
        return ResponseEntity.ok(
                businessClientService.blockClient(clientId, businessId, userDetails.getId(), requestDto.getReason()));
    }

    @PutMapping("/{clientId}/unblock")
    public ResponseEntity<BusinessClientResponseDto> unblockClient(
            @PathVariable Long businessId,
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(businessClientService.unblockClient(clientId, businessId, userDetails.getId()));
    }
}

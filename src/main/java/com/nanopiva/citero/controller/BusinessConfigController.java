package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.BusinessConfigRequestDto;
import com.nanopiva.citero.dto.business.BusinessConfigResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.BusinessConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/config")
@RequiredArgsConstructor
public class BusinessConfigController {

    private final BusinessConfigService businessConfigService;

    @GetMapping
    public ResponseEntity<BusinessConfigResponseDto> getConfig(@PathVariable Long businessId) {
        return ResponseEntity.ok(businessConfigService.getConfigByBusinessId(businessId));
    }

    @PutMapping
    public ResponseEntity<BusinessConfigResponseDto> updateConfig(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody BusinessConfigRequestDto requestDto) {
        return ResponseEntity.ok(businessConfigService.updateConfig(businessId, userDetails.getId(), requestDto));
    }
}
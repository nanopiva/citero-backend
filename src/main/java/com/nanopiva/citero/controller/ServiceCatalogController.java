package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.ServiceCreateRequestDto;
import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.dto.business.ServiceUpdateDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.ServiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    @PostMapping
    public ResponseEntity<ServiceResponseDto> createService(
            @PathVariable Long businessId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ServiceCreateRequestDto requestDto) {
        return ResponseEntity.status(201).body(serviceCatalogService.createService(businessId, userDetails.getId(), requestDto));
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponseDto>> getServices(@PathVariable Long businessId) {
        return ResponseEntity.ok(serviceCatalogService.getServicesByBusinessId(businessId));
    }

    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceResponseDto> updateService(
            @PathVariable Long serviceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody ServiceUpdateDto updateDto) {
        return ResponseEntity.ok(serviceCatalogService.updateService(serviceId, userDetails.getId(), updateDto));
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable Long serviceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        serviceCatalogService.deleteService(serviceId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
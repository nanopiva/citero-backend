package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.business.BusinessCreateRequestDto;
import com.nanopiva.citero.dto.business.BusinessResponseDto;
import com.nanopiva.citero.dto.business.BusinessUpdateDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping
    public ResponseEntity<BusinessResponseDto> createBusiness(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody BusinessCreateRequestDto requestDto) {
        return ResponseEntity.status(201).body(businessService.createBusiness(userDetails.getId(), requestDto));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<BusinessResponseDto> getBusinessBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(businessService.getBusinessBySlug(slug));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<BusinessResponseDto> getBusinessById(@PathVariable Long id) {
        return ResponseEntity.ok(businessService.getBusinessById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessResponseDto> updateBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody BusinessUpdateDto updateDto) {
        return ResponseEntity.ok(businessService.updateBusiness(id, userDetails.getId(), updateDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        businessService.deleteBusiness(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-businesses")
    public ResponseEntity<List<BusinessResponseDto>> getMyBusinesses(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(businessService.getBusinessesByOwner(userDetails.getId()));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BusinessResponseDto> uploadLogo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(businessService.updateLogo(id, userDetails.getId(), file));
    }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BusinessResponseDto> uploadCover(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(businessService.updateCover(id, userDetails.getId(), file));
    }


    @GetMapping
    public ResponseEntity<Page<BusinessResponseDto>> listBusinesses(
            @RequestParam(required = false) String name,
            Pageable pageable) {
        return ResponseEntity.ok(businessService.searchBusinesses(name, pageable));
    }
}
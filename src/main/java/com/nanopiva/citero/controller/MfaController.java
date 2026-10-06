package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.user.MfaDisableRequestDto;
import com.nanopiva.citero.dto.user.MfaEnableRequestDto;
import com.nanopiva.citero.dto.user.MfaEnableResponseDto;
import com.nanopiva.citero.dto.user.MfaSetupResponseDto;
import com.nanopiva.citero.dto.user.MfaStatusResponseDto;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Gestión del segundo factor (TOTP) de la propia cuenta. */
@RestController
@RequestMapping("/api/users/me/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    @GetMapping
    public ResponseEntity<MfaStatusResponseDto> status(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(mfaService.status(userDetails.getId()));
    }

    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponseDto> setup(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(mfaService.beginSetup(userDetails.getId()));
    }

    @PostMapping("/enable")
    public ResponseEntity<MfaEnableResponseDto> enable(@AuthenticationPrincipal UserDetailsImpl userDetails,
                                                       @Valid @RequestBody MfaEnableRequestDto request) {
        return ResponseEntity.ok(mfaService.enable(userDetails.getId(), request.getCode()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal UserDetailsImpl userDetails,
                                        @Valid @RequestBody MfaDisableRequestDto request) {
        mfaService.disable(userDetails.getId(), request.getPassword(), request.getCode());
        return ResponseEntity.noContent().build();
    }
}

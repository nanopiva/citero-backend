package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.user.OtpRequestDto;
import com.nanopiva.citero.dto.user.OtpVerifyRequestDto;
import com.nanopiva.citero.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpRequestDto requestDto) {
        // Pasamos el propósito para que el OtpService sepa que está validando una reserva de invitado
        otpService.generateAndSendOtp(requestDto.getTarget(), OtpService.PURPOSE_GUEST_VERIFICATION);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verifyOtp(@Valid @RequestBody OtpVerifyRequestDto requestDto) {
        otpService.verifyOtp(requestDto.getTarget(), requestDto.getCode(), OtpService.PURPOSE_GUEST_VERIFICATION);
        return ResponseEntity.noContent().build();
    }
}
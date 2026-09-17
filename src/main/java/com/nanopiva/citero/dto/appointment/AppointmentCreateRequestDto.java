package com.nanopiva.citero.dto.appointment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCreateRequestDto {

    @NotNull(message = "El ID del servicio es obligatorio")
    private Long serviceId;

    private Long staffId;

    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    private LocalDateTime startTime;

    // Para modo AUTHENTICATED (si el cliente no está logueado, se usa junto con otpCode)
    @Email(message = "El formato del email no es válido")
    private String guestEmail;
    private String guestPhone;
    private String otpCode;
}
package com.nanopiva.citero.dto.appointment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "El email no puede tener más de 100 caracteres")
    private String guestEmail;

    @Size(max = 20, message = "El teléfono no puede tener más de 20 caracteres")
    private String guestPhone;

    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String guestName;

    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String otpCode;
}
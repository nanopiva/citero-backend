package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUpdateDto {

    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;

    @Size(max = 1000, message = "La descripción no puede tener más de 1000 caracteres")
    private String description;

    @Size(max = 500)
    private String logoUrl;

    // Campos de contacto y ubicación (opcionales)

    @Size(max = 255, message = "La dirección no puede tener más de 255 caracteres")
    private String address;

    private Double latitude;

    private Double longitude;

    @Size(max = 30, message = "El teléfono no puede tener más de 30 caracteres")
    private String phone;

    @Size(max = 500, message = "La URL de la imagen de portada no puede superar los 500 caracteres")
    private String coverImageUrl;

    @Size(max = 255, message = "La URL de Instagram no puede superar los 255 caracteres")
    private String instagramUrl;

    @Size(max = 255, message = "La URL de Facebook no puede superar los 255 caracteres")
    private String facebookUrl;

    @Size(max = 255, message = "La URL de TikTok no puede superar los 255 caracteres")
    private String tiktokUrl;

    @Size(max = 255, message = "La URL de Twitter no puede superar los 255 caracteres")
    private String twitterUrl;

    @Size(max = 30, message = "El número de WhatsApp no puede tener más de 30 caracteres")
    private String whatsappNumber;

    @Size(max = 60, message = "La zona horaria no puede tener más de 60 caracteres")
    private String timezone;
}
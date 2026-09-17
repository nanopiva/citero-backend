package com.nanopiva.citero.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessResponseDto {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private LocalDateTime createdAt;
    private BusinessConfigResponseDto config;
    private String logoUrl;

    // Campos de contacto y ubicación

    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String coverImageUrl;
    private String instagramUrl;
    private String facebookUrl;
    private String tiktokUrl;
    private String twitterUrl;
    private String whatsappNumber;
    private String timezone;
}
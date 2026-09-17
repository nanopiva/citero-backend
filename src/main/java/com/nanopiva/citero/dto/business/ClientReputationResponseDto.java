package com.nanopiva.citero.dto.business;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ClientReputationResponseDto {
    private Long id;
    private Long clientId;
    private String clientEmail;
    private Integer strikeCount;
    private Boolean isBlocked;
    private LocalDateTime lastUpdated;
}
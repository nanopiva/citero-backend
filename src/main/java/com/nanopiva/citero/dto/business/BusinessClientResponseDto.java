package com.nanopiva.citero.dto.business;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class BusinessClientResponseDto {
    private Long id;
    private Long clientId;
    private String clientEmail;
    private Boolean isBlocked;
    private String blockReason;
    private LocalDateTime lastUpdated;
}

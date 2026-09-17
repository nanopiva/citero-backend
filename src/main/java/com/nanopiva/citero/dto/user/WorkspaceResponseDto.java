package com.nanopiva.citero.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponseDto {

    private Long businessId;
    private String businessName;
    private String slug;
    private String logoUrl;

    // Este rol es CONTEXTUAL al negocio (Devolverá "OWNER" o "STAFF")
    private String role;
}
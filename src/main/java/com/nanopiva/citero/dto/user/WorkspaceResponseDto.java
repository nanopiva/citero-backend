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

    // Zona horaria del negocio (IANA), usada por el front para comparar fechas/horas.
    private String timezone;

    // Este rol es CONTEXTUAL al negocio (Devolverá "OWNER" o "STAFF")
    private String role;
}
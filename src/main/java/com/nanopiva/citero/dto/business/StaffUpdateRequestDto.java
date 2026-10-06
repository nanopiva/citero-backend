package com.nanopiva.citero.dto.business;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Edición de un profesional: solo nombre y servicios. El email no se puede
 * cambiar (es el identificador de la vinculación con la cuenta).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffUpdateRequestDto {

    @Size(max = 100, message = "El nombre personalizado no puede tener más de 100 caracteres")
    private String customName;

    private Set<Long> serviceIds;
}

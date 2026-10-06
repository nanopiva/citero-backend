package com.nanopiva.citero.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {

    // Opcional: null o vacío = no tocar. Si viene con valor, el servicio exige 8..72.
    @Size(max = 72, message = "La contraseña no puede tener más de 72 caracteres")
    private String password;

    // Requerida sólo cuando se cambia la contraseña (reautenticación).
    @Size(max = 72, message = "La contraseña actual no puede tener más de 72 caracteres")
    private String currentPassword;

    @Size(max = 20, message = "El teléfono no puede tener más de 20 caracteres")
    private String phone;

    // Opcional: null = no tocar; string vacío = borrar.
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;
}

package com.nanopiva.citero.util;

import com.nanopiva.citero.entity.Staff;

/**
 * Utilidades para resolver de forma segura los datos de un {@link Staff},
 * incluyendo los "perfiles huérfanos" (creados por email sin que el empleado
 * se registre todavía, por lo que {@code user} es null).
 */
public final class StaffUtils {

    public static final String DEFAULT_STAFF_NAME = "Profesional";

    private StaffUtils() {
    }

    /** Indica si el empleado ya reclamó su cuenta (tiene un {@code User} asociado). */
    public static boolean hasClaimedAccount(Staff staff) {
        return staff != null && staff.getUser() != null;
    }

    /**
     * Email del empleado: el de su cuenta si la reclamó, o el de contacto si es huérfano.
     * Devuelve {@code null} si no hay ninguno disponible.
     */
    public static String email(Staff staff) {
        if (staff == null) {
            return null;
        }
        if (staff.getUser() != null) {
            return staff.getUser().getEmail();
        }
        return staff.getContactEmail();
    }

    /**
     * Nombre legible del empleado: prioriza {@code customName}; si no existe, usa el
     * prefijo del email (de cuenta o de contacto); si no hay ninguno, un fallback genérico.
     */
    public static String displayName(Staff staff) {
        if (staff == null) {
            return DEFAULT_STAFF_NAME;
        }
        if (staff.getCustomName() != null && !staff.getCustomName().isBlank()) {
            return staff.getCustomName();
        }
        String email = email(staff);
        if (email != null && !email.isBlank()) {
            return email.split("@")[0];
        }
        return DEFAULT_STAFF_NAME;
    }
}

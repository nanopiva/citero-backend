package com.nanopiva.citero.util;

import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffUtilsTest {

    @Test
    void orphanWithoutContactEmailUsesFallbacks() {
        Staff orphan = Staff.builder().build();

        assertFalse(StaffUtils.hasClaimedAccount(orphan));
        assertNull(StaffUtils.email(orphan));
        assertEquals("Profesional", StaffUtils.displayName(orphan));
    }

    @Test
    void orphanWithContactEmailUsesContactEmail() {
        Staff orphan = Staff.builder().contactEmail("juan@barber.com").build();

        assertFalse(StaffUtils.hasClaimedAccount(orphan));
        assertEquals("juan@barber.com", StaffUtils.email(orphan));
        assertEquals("juan", StaffUtils.displayName(orphan));
    }

    @Test
    void claimedAccountPrefersUserEmail() {
        User user = User.builder().email("ana@salon.com").build();
        Staff staff = Staff.builder().user(user).contactEmail("otro@mail.com").build();

        assertTrue(StaffUtils.hasClaimedAccount(staff));
        assertEquals("ana@salon.com", StaffUtils.email(staff));
        assertEquals("ana", StaffUtils.displayName(staff));
    }

    @Test
    void customNameWins() {
        User user = User.builder().email("ana@salon.com").build();
        Staff staff = Staff.builder().user(user).customName("Top Stylist").build();

        assertEquals("Top Stylist", StaffUtils.displayName(staff));
    }

    @Test
    void nullStaffIsSafe() {
        assertNull(StaffUtils.email(null));
        assertEquals("Profesional", StaffUtils.displayName(null));
        assertFalse(StaffUtils.hasClaimedAccount(null));
    }
}

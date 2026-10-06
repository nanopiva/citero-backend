package com.nanopiva.citero.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdSecurityGuardTest {

    private ProdSecurityGuard guard(boolean cookieSecure, boolean h2Console, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        ProdSecurityGuard guard = new ProdSecurityGuard(environment);
        ReflectionTestUtils.setField(guard, "cookieSecure", cookieSecure);
        ReflectionTestUtils.setField(guard, "h2ConsoleEnabled", h2Console);
        ReflectionTestUtils.setField(guard, "datasourceUrl", "jdbc:postgresql://db:5432/citero?sslmode=require");
        ReflectionTestUtils.setField(guard, "jwtSecret", "jwt-secret");
        ReflectionTestUtils.setField(guard, "publicLinkSecret", "link-secret");
        ReflectionTestUtils.setField(guard, "otpSecret", "otp-secret");
        return guard;
    }

    @Test
    void fallaEnProdSiLaCookieNoEsSecure() {
        assertThrows(IllegalStateException.class, () -> guard(false, false, "prod").verify());
    }

    @Test
    void fallaEnProdSiLaConsolaH2EstaActiva() {
        assertThrows(IllegalStateException.class, () -> guard(true, true, "prod").verify());
    }

    @Test
    void noAplicaFueraDeProd() {
        assertDoesNotThrow(() -> guard(false, true, "dev").verify());
    }

    @Test
    void prodConConfigSeguraNoFalla() {
        assertDoesNotThrow(() -> guard(true, false, "prod").verify());
    }
}

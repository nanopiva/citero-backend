package com.nanopiva.citero;

import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.service.RefreshTokenService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenFlowTest extends IntegrationTest {

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private UserRepository userRepository;

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .email("refresh-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    @Test
    void rotateIssuesNewTokenAndInvalidatesOld() {
        User user = newUser("rotate");

        var first = refreshTokenService.issue(user, "UA", "127.0.0.1");
        var second = refreshTokenService.rotate(first.rawToken(), "UA", "127.0.0.1").orElseThrow();

        assertNotEquals(first.rawToken(), second.rawToken());

        // El token viejo ya no sirve; su reuso revoca la familia completa...
        assertTrue(refreshTokenService.rotate(first.rawToken(), "UA", "127.0.0.1").isEmpty());
        // ...por lo que el token nuevo también queda invalidado.
        assertTrue(refreshTokenService.rotate(second.rawToken(), "UA", "127.0.0.1").isEmpty());
    }

    @Test
    void revokeInvalidatesSession() {
        User user = newUser("revoke");
        var pair = refreshTokenService.issue(user, "UA", "127.0.0.1");

        refreshTokenService.revoke(pair.rawToken());

        assertTrue(refreshTokenService.rotate(pair.rawToken(), "UA", "127.0.0.1").isEmpty());
    }

    @Test
    void unknownTokenIsRejected() {
        assertTrue(refreshTokenService.rotate("token-inexistente", "UA", "127.0.0.1").isEmpty());
    }
}

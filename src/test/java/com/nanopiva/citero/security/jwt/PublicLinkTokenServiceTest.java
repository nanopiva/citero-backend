package com.nanopiva.citero.security.jwt;

import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicLinkTokenServiceTest extends IntegrationTest {

    @Autowired private PublicLinkTokenService publicLinkTokenService;
    @Autowired private JwtService jwtService;

    private static final String ZONE = "America/Argentina/Buenos_Aires";

    @Test
    void tokenValidoDevuelveElIdDelTurno() {
        String token = publicLinkTokenService.generate(42L, LocalDateTime.now().plusDays(1), ZONE);

        Optional<Long> result = publicLinkTokenService.validate(token);

        assertEquals(Optional.of(42L), result);
    }

    @Test
    void tokenVacioOAlteradoEsRechazado() {
        assertTrue(publicLinkTokenService.validate(null).isEmpty());
        assertTrue(publicLinkTokenService.validate("").isEmpty());
        assertTrue(publicLinkTokenService.validate("no-es-un-token").isEmpty());

        String token = publicLinkTokenService.generate(1L, LocalDateTime.now().plusDays(1), ZONE);
        assertTrue(publicLinkTokenService.validate(token + "x").isEmpty());
    }

    @Test
    void tokenVencidoEsRechazado() {
        // startTime muy anterior a la ventana de gracia (default 720h = 30 días).
        String token = publicLinkTokenService.generate(7L, LocalDateTime.now().minusDays(60), ZONE);

        assertTrue(publicLinkTokenService.validate(token).isEmpty());
    }

    @Test
    void accessTokenJwtNoSirveComoLinkPublico() {
        User user = User.builder().id(1L).email("user@test.com").password("x").build();
        String accessToken = jwtService.generateTokenFromUserDetails(UserDetailsImpl.build(user));

        assertTrue(publicLinkTokenService.validate(accessToken).isEmpty(),
                "El access token (otro propósito) no debe autorizar la gestión pública de un turno");
    }
}

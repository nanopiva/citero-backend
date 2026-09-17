package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.ClientReputationResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ReputationServiceTest extends IntegrationTest {

    @Autowired private ReputationService reputationService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ClientReputationRepository reputationRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("rep-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String uniqueSlug(String base) {
        return base + "-" + System.nanoTime();
    }

    private Business seedBusiness(User owner, String slug, boolean enablePenalties, int maxStrikes) {
        Business business = Business.builder().owner(owner).name("Negocio " + slug).slug(slug).build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enablePenalties(enablePenalties)
                .maxStrikes(maxStrikes)
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    @Test
    void applyStrikeIncrementsAndBlocksAtMaxStrikes() {
        User owner = persistUser("block-owner");
        Business business = seedBusiness(owner, uniqueSlug("block"), true, 3);
        User client = persistUser("block-client");

        ClientReputation first = reputationService.applyStrike(client.getId(), business.getId(), "No asistió");
        assertEquals(1, first.getStrikeCount(), "El primer strike debe registrarse");
        assertFalse(first.getIsBlocked(), "Con 1 de 3 strikes el cliente no está bloqueado");

        reputationService.applyStrike(client.getId(), business.getId(), "No asistió");
        ClientReputation third = reputationService.applyStrike(client.getId(), business.getId(), "No asistió");

        assertEquals(3, third.getStrikeCount(), "El tercer strike debe registrarse");
        assertTrue(third.getIsBlocked(), "Al alcanzar maxStrikes el cliente queda bloqueado");
        assertTrue(reputationService.isClientBlocked(client.getId(), business.getId()),
                "isClientBlocked debe reflejar el bloqueo");
    }

    @Test
    void applyStrikeDoesNothingWhenPenaltiesDisabled() {
        User owner = persistUser("off-owner");
        Business business = seedBusiness(owner, uniqueSlug("off"), false, 3);
        User client = persistUser("off-client");

        ClientReputation reputation = reputationService.applyStrike(client.getId(), business.getId(), "motivo");

        assertEquals(0, reputation.getStrikeCount(), "Con sanciones deshabilitadas no se incrementan strikes");
        assertFalse(reputation.getIsBlocked(), "No debe bloquearse con sanciones deshabilitadas");
    }

    @Test
    void getReputationWithValidationRejectsNonOwner() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"), true, 3);
        User client = persistUser("perm-client");

        assertThrows(ForbiddenException.class, () -> reputationService.getReputationWithValidation(
                client.getId(), business.getId(), intruder.getId()),
                "Un no-dueño no puede ver la reputación");
    }

    @Test
    void resetStrikesClearsCountButKeepsBlockedFlag() {
        User owner = persistUser("reset-owner");
        Business business = seedBusiness(owner, uniqueSlug("reset"), true, 1);
        User client = persistUser("reset-client");
        reputationService.applyStrike(client.getId(), business.getId(), "motivo");

        ClientReputationResponseDto reset = reputationService.resetStrikes(client.getId(), business.getId(), owner.getId());

        assertEquals(0, reset.getStrikeCount(), "Los strikes deben resetearse");
        assertTrue(reset.getIsBlocked(), "resetStrikes limpia los strikes pero no desbloquea al cliente");
    }

    @Test
    void unblockClientClearsBlockAndStrikes() {
        User owner = persistUser("unblock-owner");
        Business business = seedBusiness(owner, uniqueSlug("unblock"), true, 1);
        User client = persistUser("unblock-client");
        reputationService.applyStrike(client.getId(), business.getId(), "motivo");

        ClientReputationResponseDto unblocked = reputationService.unblockClient(client.getId(), business.getId(), owner.getId());

        assertFalse(unblocked.getIsBlocked(), "El cliente debe quedar desbloqueado");
        assertEquals(0, unblocked.getStrikeCount(), "Al desbloquear también se resetean los strikes");
    }

    @Test
    void getReputationsByBusinessOnlyReturnsItsOwn() {
        User owner = persistUser("list-owner");
        Business business = seedBusiness(owner, uniqueSlug("list"), true, 3);
        User client = persistUser("list-client");
        reputationRepository.save(ClientReputation.builder()
                .client(client).business(business).strikeCount(1).isBlocked(false).build());

        User otherOwner = persistUser("list-other");
        Business otherBusiness = seedBusiness(otherOwner, uniqueSlug("list-other"), true, 3);
        reputationRepository.save(ClientReputation.builder()
                .client(client).business(otherBusiness).strikeCount(2).isBlocked(false).build());

        Page<ClientReputationResponseDto> result = reputationService
                .getReputationsByBusiness(business.getId(), owner.getId(), Pageable.unpaged());

        assertEquals(1, result.getTotalElements(), "Solo deben listarse las reputaciones del negocio indicado");
        assertEquals(client.getEmail(), result.getContent().get(0).getClientEmail());
    }
}

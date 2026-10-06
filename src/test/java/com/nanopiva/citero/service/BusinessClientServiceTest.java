package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessClientResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessClient;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.repository.BusinessClientRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BusinessClientServiceTest extends IntegrationTest {

    @Autowired private BusinessClientService businessClientService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessClientRepository businessClientRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("bc-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String uniqueSlug(String base) {
        return base + "-" + System.nanoTime();
    }

    private Business seedBusiness(User owner, String slug) {
        Business business = Business.builder().owner(owner).name("Negocio " + slug).slug(slug).build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    @Test
    void blockClientMarksBlockedWithReason() {
        User owner = persistUser("block-owner");
        Business business = seedBusiness(owner, uniqueSlug("block"));
        User client = persistUser("block-client");

        BusinessClientResponseDto blocked = businessClientService.blockClient(
                client.getId(), business.getId(), owner.getId(), "Daño en el local");

        assertTrue(blocked.getIsBlocked());
        assertEquals("Daño en el local", blocked.getBlockReason());
        assertTrue(businessClientService.isClientBlocked(client.getId(), business.getId()));
    }

    @Test
    void unblockClientLiftsBlock() {
        User owner = persistUser("unblock-owner");
        Business business = seedBusiness(owner, uniqueSlug("unblock"));
        User client = persistUser("unblock-client");
        businessClientService.blockClient(client.getId(), business.getId(), owner.getId(), "motivo");

        BusinessClientResponseDto unblocked = businessClientService.unblockClient(
                client.getId(), business.getId(), owner.getId());

        assertFalse(unblocked.getIsBlocked());
        assertNull(unblocked.getBlockReason());
        assertFalse(businessClientService.isClientBlocked(client.getId(), business.getId()));
    }

    @Test
    void getBusinessClientRejectsNonOwner() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"));
        User client = persistUser("perm-client");

        assertThrows(ForbiddenException.class, () -> businessClientService.getBusinessClient(
                client.getId(), business.getId(), intruder.getId()),
                "Un no-dueño no puede ver los clientes del negocio");
    }

    @Test
    void getBusinessClientsOnlyReturnsItsOwn() {
        User owner = persistUser("list-owner");
        Business business = seedBusiness(owner, uniqueSlug("list"));
        User client = persistUser("list-client");
        businessClientRepository.save(BusinessClient.builder()
                .client(client).business(business).isBlocked(true).build());

        User otherOwner = persistUser("list-other");
        Business otherBusiness = seedBusiness(otherOwner, uniqueSlug("list-other"));
        businessClientRepository.save(BusinessClient.builder()
                .client(client).business(otherBusiness).isBlocked(true).build());

        Page<BusinessClientResponseDto> result = businessClientService
                .getBusinessClients(business.getId(), owner.getId(), Pageable.unpaged());

        assertEquals(1, result.getTotalElements(), "Solo deben listarse los registros del negocio indicado");
        assertEquals(client.getEmail(), result.getContent().get(0).getClientEmail());
    }
}

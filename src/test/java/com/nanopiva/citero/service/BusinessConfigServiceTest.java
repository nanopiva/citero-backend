package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessConfigRequestDto;
import com.nanopiva.citero.dto.business.BusinessConfigResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class BusinessConfigServiceTest extends IntegrationTest {

    @Autowired private BusinessConfigService configService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("cfg-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String uniqueSlug(String base) {
        return base + "-" + System.nanoTime();
    }

    private Business seedBusiness(User owner, String slug, boolean withConfig) {
        Business business = Business.builder().owner(owner).name("Negocio " + slug).slug(slug).build();
        if (withConfig) {
            BusinessConfig config = BusinessConfig.builder()
                    .business(business)
                    .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                    .cancellationToleranceHours(24)
                    .defaultOpeningTime(LocalTime.of(9, 0))
                    .defaultClosingTime(LocalTime.of(18, 0))
                    .enablePenalties(true)
                    .maxStrikes(3)
                    .enableReminders(true)
                    .reminder24hEnabled(true)
                    .reminder2hEnabled(true)
                    .build();
            business.setConfig(config);
        }
        return businessRepository.save(business);
    }

    private BusinessConfigRequestDto fullRequest(String mode) {
        return BusinessConfigRequestDto.builder()
                .reservationMode(mode)
                .cancellationToleranceHours(12)
                .enablePenalties(false)
                .maxStrikes(5)
                .defaultOpeningTime(LocalTime.of(8, 0))
                .defaultClosingTime(LocalTime.of(20, 0))
                .enableReminders(false)
                .reminder24hEnabled(false)
                .reminder2hEnabled(false)
                .build();
    }

    @Test
    void getConfigReturnsDefaults() {
        User owner = persistUser("get");
        Business business = seedBusiness(owner, uniqueSlug("get"), true);

        BusinessConfigResponseDto config = configService.getConfigByBusinessId(business.getId());

        assertEquals("PUBLIC", config.getReservationMode(), "El modo por defecto es PUBLIC");
        assertEquals(24, config.getCancellationToleranceHours(), "La tolerancia por defecto es 24 horas");
        assertEquals(3, config.getMaxStrikes(), "El máximo de strikes por defecto es 3");
        assertEquals(LocalTime.of(9, 0), config.getDefaultOpeningTime());
        assertEquals(LocalTime.of(18, 0), config.getDefaultClosingTime());
    }

    @Test
    void updateConfigPersistsChanges() {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("update"), true);

        BusinessConfigResponseDto response = configService.updateConfig(
                business.getId(), owner.getId(), fullRequest("AUTHENTICATED"));

        assertEquals("AUTHENTICATED", response.getReservationMode());
        assertEquals(12, response.getCancellationToleranceHours());
        assertEquals(Boolean.FALSE, response.getEnablePenalties());
        assertEquals(5, response.getMaxStrikes());
        assertEquals(LocalTime.of(8, 0), response.getDefaultOpeningTime());
        assertEquals(LocalTime.of(20, 0), response.getDefaultClosingTime());
        assertFalse(response.getEnableReminders(), "Los recordatorios deben quedar deshabilitados");

        BusinessConfig persisted = configService.getConfigEntityByBusinessId(business.getId());
        assertEquals(BusinessConfig.ReservationMode.AUTHENTICATED, persisted.getReservationMode());
        assertEquals(5, persisted.getMaxStrikes());
    }

    @Test
    void updateConfigWithInvalidReservationModeIsRejected() {
        User owner = persistUser("bad-mode");
        Business business = seedBusiness(owner, uniqueSlug("bad-mode"), true);

        assertThrows(BadRequestException.class, () -> configService.updateConfig(
                business.getId(), owner.getId(), fullRequest("SOLO_INVITADOS")),
                "Un modo de reserva inválido debe rechazarse");
    }

    @Test
    void updateConfigByNonOwnerIsRejected() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"), true);

        assertThrows(ForbiddenException.class, () -> configService.updateConfig(
                business.getId(), intruder.getId(), fullRequest("PUBLIC")),
                "Solo el dueño puede modificar la configuración");
    }

    @Test
    void getConfigWithoutConfigFails() {
        User owner = persistUser("no-config");
        Business business = seedBusiness(owner, uniqueSlug("no-config"), false);

        assertThrows(ResourceNotFoundException.class, () -> configService.getConfigByBusinessId(business.getId()),
                "Un negocio sin configuración debe producir ResourceNotFoundException");
    }
}

package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessCreateRequestDto;
import com.nanopiva.citero.dto.business.BusinessResponseDto;
import com.nanopiva.citero.dto.business.BusinessUpdateDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BusinessServiceTest extends IntegrationTest {

    @Autowired private BusinessService businessService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleRepository scheduleRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("biz-" + tag + "-" + System.nanoTime() + "@test.com")
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
                .enablePenalties(true)
                .maxStrikes(3)
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    @Test
    void createBusinessSeedsConfigAndSevenSchedules() {
        User owner = persistUser("seed");
        String slug = uniqueSlug("seed");

        BusinessResponseDto response = businessService.createBusiness(
                owner.getId(), BusinessCreateRequestDto.builder().name("Barbería Seed").slug(slug).build());

        assertNotNull(response.getId(), "El negocio debe persistirse con un ID");
        assertNotNull(response.getConfig(), "El negocio debe crearse con configuración por defecto");
        assertEquals("PUBLIC", response.getConfig().getReservationMode(), "El modo de reserva por defecto es PUBLIC");
        assertEquals(24, response.getConfig().getCancellationToleranceHours(), "La tolerancia por defecto es 24 horas");
        assertEquals(Boolean.TRUE, response.getConfig().getEnablePenalties(), "Las sanciones vienen habilitadas");
        assertEquals(3, response.getConfig().getMaxStrikes(), "El máximo de strikes por defecto es 3");
        assertEquals(LocalTime.of(9, 0), response.getConfig().getDefaultOpeningTime(), "La apertura por defecto es 09:00");
        assertEquals(LocalTime.of(18, 0), response.getConfig().getDefaultClosingTime(), "El cierre por defecto es 18:00");

        Business saved = businessRepository.findById(response.getId()).orElseThrow();
        assertEquals(7, scheduleRepository.findByBusiness(saved).size(),
                "Se debe sembrar un horario por cada día de la semana");
    }

    @Test
    void createBusinessRejectsDuplicateSlug() {
        User owner = persistUser("dup");
        String slug = uniqueSlug("dup");
        businessService.createBusiness(owner.getId(),
                BusinessCreateRequestDto.builder().name("Primero").slug(slug).build());

        assertThrows(DuplicateResourceException.class, () -> businessService.createBusiness(
                owner.getId(), BusinessCreateRequestDto.builder().name("Segundo").slug(slug).build()),
                "Un slug ya usado debe rechazarse");
    }

    @Test
    void createBusinessWithUnknownOwnerFails() {
        assertThrows(ResourceNotFoundException.class, () -> businessService.createBusiness(
                999_999L, BusinessCreateRequestDto.builder().name("Fantasma").slug(uniqueSlug("ghost")).build()),
                "Un dueño inexistente debe producir ResourceNotFoundException");
    }

    @Test
    void updateBusinessOnlyByOwner() {
        User owner = persistUser("upd");
        Business business = seedBusiness(owner, uniqueSlug("upd"));

        BusinessResponseDto response = businessService.updateBusiness(business.getId(), owner.getId(),
                BusinessUpdateDto.builder().name("Nuevo nombre").description("Nueva descripción").phone("555-1234").build());

        assertEquals("Nuevo nombre", response.getName(), "El nombre debe actualizarse");
        assertEquals("Nueva descripción", response.getDescription(), "La descripción debe actualizarse");
        assertEquals("555-1234", response.getPhone(), "El teléfono debe actualizarse");

        Business reloaded = businessRepository.findById(business.getId()).orElseThrow();
        assertEquals("Nuevo nombre", reloaded.getName(), "El cambio debe quedar persistido");
    }

    @Test
    void updateBusinessByDifferentOwnerIsRejected() {
        User owner = persistUser("upd-owner");
        User intruder = persistUser("upd-intruder");
        Business business = seedBusiness(owner, uniqueSlug("upd-denied"));

        assertThrows(ForbiddenException.class, () -> businessService.updateBusiness(
                business.getId(), intruder.getId(), BusinessUpdateDto.builder().name("Hackeado").build()),
                "Un usuario que no es dueño no puede modificar el negocio");
    }

    @Test
    void deleteBusinessRemovesItAndItsSchedules() {
        User owner = persistUser("del");
        String slug = uniqueSlug("del");
        BusinessResponseDto created = businessService.createBusiness(
                owner.getId(), BusinessCreateRequestDto.builder().name("A borrar").slug(slug).build());

        Business saved = businessRepository.findById(created.getId()).orElseThrow();
        List<Long> scheduleIds = scheduleRepository.findByBusiness(saved).stream()
                .map(BusinessSchedule::getId)
                .toList();

        businessService.deleteBusiness(created.getId(), owner.getId());
        businessRepository.flush();

        assertTrue(businessRepository.findById(created.getId()).isEmpty(), "El negocio debe eliminarse");
        assertTrue(scheduleIds.stream().allMatch(id -> scheduleRepository.findById(id).isEmpty()),
                "Los horarios del negocio deben eliminarse en cascada");
    }

    @Test
    void deleteBusinessByDifferentOwnerIsRejected() {
        User owner = persistUser("del-owner");
        User intruder = persistUser("del-intruder");
        Business business = seedBusiness(owner, uniqueSlug("del-denied"));

        assertThrows(ForbiddenException.class, () -> businessService.deleteBusiness(business.getId(), intruder.getId()),
                "Un usuario que no es dueño no puede eliminar el negocio");
        assertTrue(businessRepository.findById(business.getId()).isPresent(), "El negocio debe seguir existiendo");
    }

    @Test
    void searchBusinessesIsPaginatedAndFiltersByName() {
        User owner = persistUser("search");
        String token = "busqueda" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            businessService.createBusiness(owner.getId(), BusinessCreateRequestDto.builder()
                    .name("Negocio " + token + " " + i)
                    .slug(uniqueSlug("search-" + i))
                    .build());
        }

        Page<BusinessResponseDto> page = businessService.searchBusinesses(token, PageRequest.of(0, 2));

        assertEquals(3L, page.getTotalElements(), "Deben encontrarse los 3 negocios con el término buscado");
        assertEquals(2, page.getContent().size(), "La primera página debe respetar el tamaño solicitado");
    }

    @Test
    void getBusinessesByOwnerReturnsOnlyOwnedBusinesses() {
        User owner = persistUser("mine");
        User other = persistUser("other");
        seedBusiness(owner, uniqueSlug("mine-a"));
        seedBusiness(owner, uniqueSlug("mine-b"));
        seedBusiness(other, uniqueSlug("other-a"));

        List<BusinessResponseDto> mine = businessService.getBusinessesByOwner(owner.getId());

        assertEquals(2, mine.size(), "Solo deben listarse los negocios del dueño indicado");
    }
}

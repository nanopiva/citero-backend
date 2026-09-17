package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessScheduleRequestDto;
import com.nanopiva.citero.dto.business.BusinessScheduleResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BusinessScheduleServiceTest extends IntegrationTest {

    @Autowired private BusinessScheduleService scheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleRepository scheduleRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("sched-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private BusinessScheduleRequestDto dto(String day, LocalTime open, LocalTime close, boolean closed) {
        return BusinessScheduleRequestDto.builder()
                .dayOfWeek(day)
                .openTime(open)
                .closeTime(close)
                .isClosed(closed)
                .build();
    }

    @Test
    void updateWeeklyScheduleReplacesExistingSchedules() {
        User owner = persistUser("replace");
        Business business = seedBusiness(owner, uniqueSlug("replace"));
        scheduleRepository.save(BusinessSchedule.builder()
                .business(business)
                .dayOfWeek(BusinessSchedule.DayOfWeek.MONDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .isClosed(false)
                .build());

        List<BusinessScheduleResponseDto> result = scheduleService.updateWeeklySchedule(business.getId(), owner.getId(),
                List.of(
                        dto("MONDAY", LocalTime.of(8, 0), LocalTime.of(17, 0), false),
                        dto("TUESDAY", LocalTime.of(8, 0), LocalTime.of(17, 0), false)));

        assertEquals(2, result.size(), "Deben persistirse los 2 horarios enviados");
        List<BusinessSchedule> persisted = scheduleRepository.findByBusiness(business);
        assertEquals(2, persisted.size(), "El horario previo debe reemplazarse por completo");
        assertTrue(persisted.stream().allMatch(s -> s.getOpenTime().equals(LocalTime.of(8, 0))),
                "Los horarios deben tener la nueva hora de apertura");
    }

    @Test
    void updateWeeklyScheduleWithInvalidDayIsRejected() {
        User owner = persistUser("bad-day");
        Business business = seedBusiness(owner, uniqueSlug("bad-day"));

        assertThrows(BadRequestException.class, () -> scheduleService.updateWeeklySchedule(
                business.getId(), owner.getId(),
                List.of(dto("FUNDAY", LocalTime.of(8, 0), LocalTime.of(17, 0), false))),
                "Un día inválido debe rechazarse");
    }

    @Test
    void updateWeeklyScheduleByNonOwnerIsRejected() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"));

        assertThrows(ForbiddenException.class, () -> scheduleService.updateWeeklySchedule(
                business.getId(), intruder.getId(),
                List.of(dto("MONDAY", LocalTime.of(8, 0), LocalTime.of(17, 0), false))),
                "Solo el dueño puede modificar los horarios");
    }

    @Test
    void getScheduleByBusinessIdReturnsPersistedSchedules() {
        User owner = persistUser("get");
        Business business = seedBusiness(owner, uniqueSlug("get"));
        scheduleRepository.save(BusinessSchedule.builder()
                .business(business)
                .dayOfWeek(BusinessSchedule.DayOfWeek.SATURDAY)
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(14, 0))
                .isClosed(false)
                .build());

        List<BusinessScheduleResponseDto> result = scheduleService.getScheduleByBusinessId(business.getId());

        assertEquals(1, result.size());
        assertEquals("SATURDAY", result.get(0).getDayOfWeek());
        assertEquals(LocalTime.of(10, 0), result.get(0).getOpenTime());
    }
}

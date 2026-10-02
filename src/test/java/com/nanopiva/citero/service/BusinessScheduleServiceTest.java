package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessScheduleRequestDto;
import com.nanopiva.citero.dto.business.BusinessScheduleResponseDto;
import com.nanopiva.citero.dto.business.EffectiveScheduleResponseDto;
import com.nanopiva.citero.dto.business.ScheduleExceptionRequestDto;
import com.nanopiva.citero.dto.business.ScheduleExceptionResponseDto;
import com.nanopiva.citero.dto.business.SchedulePeriodDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessScheduleDay;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleDayRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BusinessScheduleServiceTest extends IntegrationTest {

    @Autowired private BusinessScheduleService scheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleDayRepository scheduleDayRepository;

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

    private List<SchedulePeriodDto> periods(LocalTime... openClosePairs) {
        List<SchedulePeriodDto> periods = new ArrayList<>();
        for (int i = 0; i + 1 < openClosePairs.length; i += 2) {
            periods.add(new SchedulePeriodDto(openClosePairs[i], openClosePairs[i + 1]));
        }
        return periods;
    }

    private BusinessScheduleRequestDto weekly(String day, boolean closed, LocalTime... pairs) {
        return BusinessScheduleRequestDto.builder()
                .dayOfWeek(day)
                .isClosed(closed)
                .periods(periods(pairs))
                .build();
    }

    private ScheduleExceptionRequestDto exception(LocalDate date, boolean closed, LocalTime... pairs) {
        return ScheduleExceptionRequestDto.builder()
                .date(date)
                .isClosed(closed)
                .periods(periods(pairs))
                .build();
    }

    // ---------------------------------------------------------------------
    // Horario semanal
    // ---------------------------------------------------------------------

    @Test
    void updateWeeklyScheduleReplacesExistingSchedules() {
        User owner = persistUser("replace");
        Business business = seedBusiness(owner, uniqueSlug("replace"));
        scheduleDayRepository.save(BusinessScheduleDay.builder()
                .business(business)
                .dayOfWeek(BusinessScheduleDay.DayOfWeek.MONDAY)
                .isClosed(false)
                .build());

        List<BusinessScheduleResponseDto> result = scheduleService.updateWeeklySchedule(business.getId(), owner.getId(),
                List.of(
                        weekly("MONDAY", false, LocalTime.of(8, 0), LocalTime.of(17, 0)),
                        weekly("TUESDAY", false, LocalTime.of(8, 0), LocalTime.of(17, 0))));

        assertEquals(2, result.size(), "Deben persistirse los 2 horarios enviados");
        List<BusinessScheduleDay> persisted = scheduleDayRepository.findByBusinessAndDayOfWeekIsNotNull(business);
        assertEquals(2, persisted.size(), "El horario previo debe reemplazarse por completo");
        assertTrue(persisted.stream().allMatch(s -> s.getPeriods().get(0).getOpenTime().equals(LocalTime.of(8, 0))),
                "Los horarios deben tener la nueva hora de apertura");
    }

    @Test
    void updateWeeklyScheduleMergesAdjacentPeriods() {
        User owner = persistUser("merge");
        Business business = seedBusiness(owner, uniqueSlug("merge"));

        List<BusinessScheduleResponseDto> result = scheduleService.updateWeeklySchedule(
                business.getId(), owner.getId(),
                List.of(weekly("MONDAY", false,
                        LocalTime.of(9, 0), LocalTime.of(12, 0),
                        LocalTime.of(12, 0), LocalTime.of(18, 0))));

        assertEquals(1, result.get(0).getPeriods().size(),
                "Dos franjas adyacentes deben normalizarse en una sola");
        assertEquals(LocalTime.of(9, 0), result.get(0).getPeriods().get(0).getOpenTime());
        assertEquals(LocalTime.of(18, 0), result.get(0).getPeriods().get(0).getCloseTime());
    }

    @Test
    void updateWeeklyScheduleRejectsOpenDayWithoutPeriods() {
        User owner = persistUser("empty");
        Business business = seedBusiness(owner, uniqueSlug("empty"));

        assertThrows(BadRequestException.class, () -> scheduleService.updateWeeklySchedule(
                business.getId(), owner.getId(), List.of(weekly("MONDAY", false))),
                "Un día abierto necesita al menos una franja");
    }

    @Test
    void updateWeeklyScheduleWithInvalidDayIsRejected() {
        User owner = persistUser("bad-day");
        Business business = seedBusiness(owner, uniqueSlug("bad-day"));

        assertThrows(BadRequestException.class, () -> scheduleService.updateWeeklySchedule(
                business.getId(), owner.getId(),
                List.of(weekly("FUNDAY", false, LocalTime.of(8, 0), LocalTime.of(17, 0)))),
                "Un día inválido debe rechazarse");
    }

    @Test
    void updateWeeklyScheduleByNonOwnerIsRejected() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"));

        assertThrows(ForbiddenException.class, () -> scheduleService.updateWeeklySchedule(
                business.getId(), intruder.getId(),
                List.of(weekly("MONDAY", false, LocalTime.of(8, 0), LocalTime.of(17, 0)))),
                "Solo el dueño puede modificar los horarios");
    }

    @Test
    void getWeeklyScheduleReturnsPersistedSchedules() {
        User owner = persistUser("get");
        Business business = seedBusiness(owner, uniqueSlug("get"));
        BusinessScheduleDay day = BusinessScheduleDay.builder()
                .business(business)
                .dayOfWeek(BusinessScheduleDay.DayOfWeek.SATURDAY)
                .isClosed(false)
                .build();
        day.getPeriods().add(com.nanopiva.citero.entity.BusinessSchedulePeriod.builder()
                .scheduleDay(day).openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(14, 0)).build());
        scheduleDayRepository.save(day);

        List<BusinessScheduleResponseDto> result = scheduleService.getWeeklySchedule(business.getId(), owner.getId());

        assertEquals(1, result.size());
        assertEquals("SATURDAY", result.get(0).getDayOfWeek());
        assertEquals(LocalTime.of(10, 0), result.get(0).getPeriods().get(0).getOpenTime());
    }

    // ---------------------------------------------------------------------
    // Excepciones + horario efectivo
    // ---------------------------------------------------------------------

    @Test
    void upsertExceptionCreatesAndReplacesTheDate() {
        User owner = persistUser("exc");
        Business business = seedBusiness(owner, uniqueSlug("exc"));
        LocalDate date = LocalDate.now().plusDays(1);

        scheduleService.upsertException(business.getId(), owner.getId(),
                exception(date, false, LocalTime.of(10, 0), LocalTime.of(13, 0)));
        ScheduleExceptionResponseDto updated = scheduleService.upsertException(business.getId(), owner.getId(),
                exception(date, false, LocalTime.of(8, 0), LocalTime.of(12, 0)));

        assertEquals(date, updated.getDate());
        assertEquals(1, updated.getPeriods().size(), "Reemplaza la excepción de esa fecha");
        assertEquals(LocalTime.of(8, 0), updated.getPeriods().get(0).getOpenTime());
        assertEquals(1, scheduleService.getExceptions(business.getId(), owner.getId()).size());
    }

    @Test
    void deleteExceptionRemovesIt() {
        User owner = persistUser("exc-del");
        Business business = seedBusiness(owner, uniqueSlug("exc-del"));
        LocalDate date = LocalDate.now().plusDays(2);
        scheduleService.upsertException(business.getId(), owner.getId(),
                exception(date, false, LocalTime.of(10, 0), LocalTime.of(13, 0)));

        scheduleService.deleteException(business.getId(), owner.getId(), date);

        assertTrue(scheduleService.getExceptions(business.getId(), owner.getId()).isEmpty());
    }

    @Test
    void upsertExceptionRejectsPastDate() {
        User owner = persistUser("exc-past");
        Business business = seedBusiness(owner, uniqueSlug("exc-past"));

        assertThrows(BadRequestException.class, () -> scheduleService.upsertException(
                business.getId(), owner.getId(),
                exception(LocalDate.now().minusDays(1), false, LocalTime.of(10, 0), LocalTime.of(13, 0))));
    }

    @Test
    void getEffectiveSchedulePrefersExceptionOverWeekly() {
        User owner = persistUser("eff");
        Business business = seedBusiness(owner, uniqueSlug("eff"));
        LocalDate date = LocalDate.now().plusDays(1);
        String dayOfWeek = date.getDayOfWeek().name();
        scheduleService.updateWeeklySchedule(business.getId(), owner.getId(),
                List.of(weekly(dayOfWeek, false, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        scheduleService.upsertException(business.getId(), owner.getId(),
                exception(date, false, LocalTime.of(10, 0), LocalTime.of(14, 0)));

        EffectiveScheduleResponseDto onDate = scheduleService.getEffectiveSchedule(business.getId(), date, owner.getId());
        assertEquals(1, onDate.getPeriods().size());
        assertEquals(LocalTime.of(10, 0), onDate.getPeriods().get(0).getOpenTime(),
                "La excepción gana sobre la regla semanal");

        EffectiveScheduleResponseDto nextWeek = scheduleService.getEffectiveSchedule(business.getId(), date.plusWeeks(1), owner.getId());
        assertEquals(LocalTime.of(9, 0), nextWeek.getPeriods().get(0).getOpenTime(),
                "Sin excepción, aplica la regla semanal");
    }
}

package com.nanopiva.citero;

import com.nanopiva.citero.dto.appointment.AvailabilityResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.service.AvailabilityService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AvailabilityFallbackTest extends IntegrationTest {

    @Autowired private AvailabilityService availabilityService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleRepository scheduleRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;

    private Business business;
    private com.nanopiva.citero.entity.Service service;

    private void setUpBusinessWithoutSchedules(String slug) {
        User owner = userRepository.save(User.builder().email("owner-" + slug + "@test.com").password("x").build());
        business = Business.builder().owner(owner).name("Fallback").slug(slug).build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .build();
        business.setConfig(config);
        business = businessRepository.save(business);

        service = serviceRepository.save(com.nanopiva.citero.entity.Service.builder()
                .business(business)
                .name("Corte")
                .durationMinutes(30)
                .price(BigDecimal.TEN)
                .build());

        staffRepository.save(Staff.builder()
                .business(business)
                .customName("Ana")
                .services(new HashSet<>(Set.of(service)))
                .build());
    }

    @Test
    void usesConfigDefaultsWhenNoScheduleExists() {
        setUpBusinessWithoutSchedules("fallback-no-schedule");
        LocalDate date = LocalDate.now().plusDays(1);

        AvailabilityResponseDto response = availabilityService.getAvailableSlots(
                business.getId(), service.getId(), null, date);

        List<LocalTime> slots = response.getAvailableSlots();
        assertFalse(slots.isEmpty(), "Debe ofrecer turnos usando los horarios por defecto de la config");
        assertTrue(slots.stream().allMatch(t -> !t.isBefore(LocalTime.of(9, 0))));
        LocalTime last = slots.get(slots.size() - 1);
        assertFalse(last.plusMinutes(30).isAfter(LocalTime.of(18, 0)));
    }

    @Test
    void validateSlotRulesFallsBackToConfigDefaults() {
        setUpBusinessWithoutSchedules("fallback-validate");
        LocalDate date = LocalDate.now().plusDays(1);

        // 09:00 entra dentro de los defaults de la config
        availabilityService.validateSlotRules(business, service, LocalDateTime.of(date, LocalTime.of(9, 0)));

        // 08:00 queda fuera del horario por defecto
        assertThrows(BadRequestException.class, () ->
                availabilityService.validateSlotRules(business, service, LocalDateTime.of(date, LocalTime.of(8, 0))));
    }

    @Test
    void explicitlyClosedDayReturnsNoSlots() {
        setUpBusinessWithoutSchedules("fallback-closed");
        LocalDate date = LocalDate.now().plusDays(1);

        scheduleRepository.save(BusinessSchedule.builder()
                .business(business)
                .dayOfWeek(BusinessSchedule.DayOfWeek.valueOf(date.getDayOfWeek().name()))
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .isClosed(true)
                .build());

        AvailabilityResponseDto response = availabilityService.getAvailableSlots(
                business.getId(), service.getId(), null, date);

        assertTrue(response.getAvailableSlots().isEmpty(), "Un día cerrado no debe ofrecer turnos");
    }
}

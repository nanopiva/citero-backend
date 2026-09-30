package com.nanopiva.citero;

import com.nanopiva.citero.dto.business.BusinessCreateRequestDto;
import com.nanopiva.citero.dto.business.BusinessResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessScheduleDay;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleDayRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.service.BusinessService;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class BusinessCreationSchedulesTest extends IntegrationTest {

    @Autowired private BusinessService businessService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessScheduleDayRepository scheduleDayRepository;

    @Test
    void creatingBusinessSeedsSevenOpenSchedules() {
        User owner = userRepository.save(User.builder().email("owner-sched@test.com").password("x").build());
        BusinessCreateRequestDto dto = BusinessCreateRequestDto.builder()
                .name("Barbería Schedules")
                .slug("barberia-schedules")
                .build();

        BusinessResponseDto response = businessService.createBusiness(owner.getId(), dto);
        Business business = businessRepository.findById(response.getId()).orElseThrow();

        List<BusinessScheduleDay> schedules =
                scheduleDayRepository.findByBusinessAndDayOfWeekIsNotNull(business);

        assertEquals(7, schedules.size(), "Debe haber un horario por cada día de la semana");
        assertEquals(7, schedules.stream().map(BusinessScheduleDay::getDayOfWeek).distinct().count());
        assertTrue(schedules.stream().allMatch(s -> Boolean.FALSE.equals(s.getIsClosed())));
        assertTrue(schedules.stream().allMatch(s ->
                        s.getPeriods().size() == 1
                                && s.getPeriods().get(0).getOpenTime().equals(LocalTime.of(9, 0))
                                && s.getPeriods().get(0).getCloseTime().equals(LocalTime.of(18, 0))),
                "Cada día abierto arranca con una franja 09:00-18:00");
    }
}

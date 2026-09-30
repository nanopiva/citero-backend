package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessScheduleDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessScheduleDayRepository extends JpaRepository<BusinessScheduleDay, Long> {

    // Reglas semanales del negocio (day_of_week no nulo).
    List<BusinessScheduleDay> findByBusinessAndDayOfWeekIsNotNull(Business business);

    // Excepciones por fecha, ordenadas cronológicamente.
    List<BusinessScheduleDay> findByBusinessAndSpecificDateIsNotNullOrderBySpecificDateAsc(Business business);

    // Regla semanal de un día puntual.
    Optional<BusinessScheduleDay> findByBusinessAndDayOfWeek(Business business, BusinessScheduleDay.DayOfWeek dayOfWeek);

    // Excepción de una fecha puntual.
    Optional<BusinessScheduleDay> findByBusinessAndSpecificDate(Business business, LocalDate specificDate);
}

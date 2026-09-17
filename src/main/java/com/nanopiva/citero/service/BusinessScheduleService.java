package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessScheduleRequestDto;
import com.nanopiva.citero.dto.business.BusinessScheduleResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class BusinessScheduleService {

    private final BusinessScheduleRepository scheduleRepository;
    private final BusinessRepository businessRepository;

    public BusinessScheduleService(BusinessScheduleRepository scheduleRepository, BusinessRepository businessRepository) {
        this.scheduleRepository = scheduleRepository;
        this.businessRepository = businessRepository;
    }

    /**
     * Obtiene todos los horarios de un negocio.
     */
    @Transactional(readOnly = true)
    public List<BusinessScheduleResponseDto> getScheduleByBusinessId(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        List<BusinessSchedule> schedules = scheduleRepository.findByBusiness(business);
        return schedules.stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    /**
     * UC-07: Actualiza todos los horarios semanales de un negocio (reemplazo completo).
     */
    @Transactional
    public List<BusinessScheduleResponseDto> updateWeeklySchedule(
            Long businessId,
            Long ownerId,
            List<BusinessScheduleRequestDto> requestDtos) {

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este negocio.");
        }

        // El flush fuerza que los DELETE lleguen a la base antes de los INSERT (Hibernate
        // ordena INSERTs antes que DELETEs), evitando violar el unique
        // (business_id, day_of_week) al reemplazar.
        List<BusinessSchedule> existingSchedules = scheduleRepository.findByBusiness(business);
        scheduleRepository.deleteAll(existingSchedules);
        scheduleRepository.flush();

        List<BusinessSchedule> newSchedules = new ArrayList<>();
        Set<BusinessSchedule.DayOfWeek> seenDays = EnumSet.noneOf(BusinessSchedule.DayOfWeek.class);
        for (BusinessScheduleRequestDto dto : requestDtos) {
            BusinessSchedule schedule = new BusinessSchedule();
            schedule.setBusiness(business);

            BusinessSchedule.DayOfWeek day;
            try {
                day = BusinessSchedule.DayOfWeek.valueOf(dto.getDayOfWeek().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Día de la semana inválido: " + dto.getDayOfWeek());
            }
            if (!seenDays.add(day)) {
                throw new BadRequestException("Día de la semana duplicado: " + dto.getDayOfWeek());
            }
            schedule.setDayOfWeek(day);

            schedule.setOpenTime(dto.getOpenTime());
            schedule.setCloseTime(dto.getCloseTime());
            schedule.setIsClosed(dto.getIsClosed());

            newSchedules.add(schedule);
        }

        List<BusinessSchedule> savedSchedules = scheduleRepository.saveAll(newSchedules);

        return savedSchedules.stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    private BusinessScheduleResponseDto mapToResponseDto(BusinessSchedule schedule) {
        return BusinessScheduleResponseDto.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek().name())
                .openTime(schedule.getOpenTime())
                .closeTime(schedule.getCloseTime())
                .isClosed(schedule.getIsClosed())
                .build();
    }
}
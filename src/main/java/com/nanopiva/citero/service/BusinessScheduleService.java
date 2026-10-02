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
import com.nanopiva.citero.entity.BusinessSchedulePeriod;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleDayRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.util.BusinessTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BusinessScheduleService {

    private final BusinessScheduleDayRepository scheduleDayRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    public BusinessScheduleService(BusinessScheduleDayRepository scheduleDayRepository,
                                   BusinessRepository businessRepository,
                                   UserRepository userRepository,
                                   StaffRepository staffRepository) {
        this.scheduleDayRepository = scheduleDayRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
    }

    /** Una franja horaria [open, close). */
    public record TimeRange(LocalTime open, LocalTime close) {
    }

    /** Resultado de resolver un día: si está cerrado y, si no, sus franjas. */
    public record EffectiveSchedule(boolean closed, List<TimeRange> ranges) {
    }

    // ---------------------------------------------------------------------
    // Reglas semanales
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BusinessScheduleResponseDto> getWeeklySchedule(Long businessId, Long viewerId) {
        Business business = getBusiness(businessId);
        assertCanView(business, viewerId);
        return scheduleDayRepository.findByBusinessAndDayOfWeekIsNotNull(business).stream()
                .sorted(Comparator.comparing(day -> day.getDayOfWeek().ordinal()))
                .map(this::toWeeklyResponse)
                .toList();
    }

    /**
     * Reemplaza por completo las reglas semanales del negocio. Las excepciones por
     * fecha no se tocan. Las franjas adyacentes o solapadas se normalizan en una sola.
     */
    @Transactional
    public List<BusinessScheduleResponseDto> updateWeeklySchedule(
            Long businessId, Long ownerId, List<BusinessScheduleRequestDto> requestDtos) {

        Business business = getOwnedBusiness(businessId, ownerId);

        // Se borran las reglas semanales previas (las franjas caen en cascada) antes de
        // insertar las nuevas, para no violar el unique (business_id, day_of_week).
        List<BusinessScheduleDay> existing = scheduleDayRepository.findByBusinessAndDayOfWeekIsNotNull(business);
        scheduleDayRepository.deleteAll(existing);
        scheduleDayRepository.flush();

        List<BusinessScheduleDay> newDays = new ArrayList<>();
        Set<BusinessScheduleDay.DayOfWeek> seenDays = EnumSet.noneOf(BusinessScheduleDay.DayOfWeek.class);
        for (BusinessScheduleRequestDto dto : requestDtos) {
            BusinessScheduleDay.DayOfWeek day;
            try {
                day = BusinessScheduleDay.DayOfWeek.valueOf(dto.getDayOfWeek().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Día de la semana inválido: " + dto.getDayOfWeek());
            }
            if (!seenDays.add(day)) {
                throw new BadRequestException("Día de la semana duplicado: " + dto.getDayOfWeek());
            }

            BusinessScheduleDay scheduleDay = BusinessScheduleDay.builder()
                    .business(business)
                    .dayOfWeek(day)
                    .isClosed(dto.getIsClosed())
                    .build();
            scheduleDay.getPeriods().addAll(buildPeriods(scheduleDay, dto.getPeriods(), dto.getIsClosed()));
            newDays.add(scheduleDay);
        }

        return scheduleDayRepository.saveAll(newDays).stream()
                .sorted(Comparator.comparing(day -> day.getDayOfWeek().ordinal()))
                .map(this::toWeeklyResponse)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Excepciones por fecha
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ScheduleExceptionResponseDto> getExceptions(Long businessId, Long viewerId) {
        Business business = getBusiness(businessId);
        assertCanView(business, viewerId);
        return scheduleDayRepository.findByBusinessAndSpecificDateIsNotNullOrderBySpecificDateAsc(business).stream()
                .map(this::toExceptionResponse)
                .toList();
    }

    /** Crea o reemplaza la excepción de una fecha puntual. */
    @Transactional
    public ScheduleExceptionResponseDto upsertException(
            Long businessId, Long ownerId, ScheduleExceptionRequestDto requestDto) {

        Business business = getOwnedBusiness(businessId, ownerId);
        LocalDate date = requestDto.getDate();
        if (date == null) {
            throw new BadRequestException("La fecha es obligatoria.");
        }
        if (date.isBefore(BusinessTime.now(business).toLocalDate())) {
            throw new BadRequestException("No se pueden crear excepciones para fechas pasadas.");
        }

        BusinessScheduleDay scheduleDay = scheduleDayRepository
                .findByBusinessAndSpecificDate(business, date)
                .orElseGet(() -> BusinessScheduleDay.builder()
                        .business(business)
                        .specificDate(date)
                        .build());

        scheduleDay.setIsClosed(requestDto.getIsClosed());
        scheduleDay.getPeriods().clear();
        scheduleDay.getPeriods().addAll(buildPeriods(scheduleDay, requestDto.getPeriods(), requestDto.getIsClosed()));

        return toExceptionResponse(scheduleDayRepository.save(scheduleDay));
    }

    @Transactional
    public void deleteException(Long businessId, Long ownerId, LocalDate date) {
        Business business = getOwnedBusiness(businessId, ownerId);
        scheduleDayRepository.findByBusinessAndSpecificDate(business, date)
                .ifPresent(scheduleDayRepository::delete);
    }

    // ---------------------------------------------------------------------
    // Horario efectivo
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public EffectiveScheduleResponseDto getEffectiveSchedule(Long businessId, LocalDate date, Long viewerId) {
        Business business = getBusiness(businessId);
        assertCanView(business, viewerId);
        EffectiveSchedule effective = resolveEffective(business, date);
        return EffectiveScheduleResponseDto.builder()
                .date(date)
                .isClosed(effective.closed())
                .periods(effective.ranges().stream()
                        .map(range -> new SchedulePeriodDto(range.open(), range.close()))
                        .toList())
                .build();
    }

    /**
     * Resuelve el horario de una fecha: primero busca una <b>excepción</b> puntual; si no
     * hay, la <b>regla semanal</b> del día; si tampoco, el valor por defecto de la
     * configuración del negocio.
     */
    @Transactional(readOnly = true)
    public EffectiveSchedule resolveEffective(Business business, LocalDate date) {
        Optional<BusinessScheduleDay> exception = scheduleDayRepository.findByBusinessAndSpecificDate(business, date);
        if (exception.isPresent()) {
            return toEffective(exception.get());
        }

        BusinessScheduleDay.DayOfWeek dayOfWeek = BusinessScheduleDay.DayOfWeek.valueOf(date.getDayOfWeek().name());
        Optional<BusinessScheduleDay> weekly = scheduleDayRepository.findByBusinessAndDayOfWeek(business, dayOfWeek);
        if (weekly.isPresent()) {
            return toEffective(weekly.get());
        }

        BusinessConfig config = business.getConfig();
        if (config != null
                && config.getDefaultOpeningTime() != null
                && config.getDefaultClosingTime() != null
                && config.getDefaultOpeningTime().isBefore(config.getDefaultClosingTime())) {
            return new EffectiveSchedule(false,
                    List.of(new TimeRange(config.getDefaultOpeningTime(), config.getDefaultClosingTime())));
        }

        return new EffectiveSchedule(true, List.of());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Business getBusiness(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
    }

    private Business getOwnedBusiness(Long businessId, Long ownerId) {
        Business business = getBusiness(businessId);
        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este negocio.");
        }
        return business;
    }

    /** El horario de un negocio solo lo consultan su dueño o su equipo. */
    private void assertCanView(Business business, Long viewerId) {
        if (viewerId == null) {
            throw new ForbiddenException("No tienes permiso para ver los horarios de este negocio.");
        }
        if (business.getOwner().getId().equals(viewerId)) {
            return;
        }
        boolean isStaff = userRepository.findById(viewerId)
                .flatMap(user -> staffRepository.findByUserAndBusiness(user, business))
                .isPresent();
        if (!isStaff) {
            throw new ForbiddenException("No tienes permiso para ver los horarios de este negocio.");
        }
    }

    /**
     * Valida y normaliza las franjas: exige apertura &lt; cierre, ordena por apertura y
     * une las adyacentes o solapadas en una sola.
     */
    private List<BusinessSchedulePeriod> buildPeriods(
            BusinessScheduleDay day, List<SchedulePeriodDto> dtos, Boolean isClosed) {

        List<TimeRange> ranges = new ArrayList<>();
        if (dtos != null) {
            for (SchedulePeriodDto dto : dtos) {
                if (dto.getOpenTime() == null || dto.getCloseTime() == null) {
                    throw new BadRequestException("Cada franja necesita hora de apertura y de cierre.");
                }
                if (!dto.getOpenTime().isBefore(dto.getCloseTime())) {
                    throw new BadRequestException("La hora de apertura debe ser anterior a la de cierre.");
                }
                ranges.add(new TimeRange(dto.getOpenTime(), dto.getCloseTime()));
            }
        }

        ranges.sort(Comparator.comparing(TimeRange::open));
        List<TimeRange> merged = mergeAdjacent(ranges);

        if (!Boolean.TRUE.equals(isClosed) && merged.isEmpty()) {
            throw new BadRequestException("Un día abierto necesita al menos una franja horaria.");
        }

        List<BusinessSchedulePeriod> periods = new ArrayList<>();
        for (TimeRange range : merged) {
            periods.add(BusinessSchedulePeriod.builder()
                    .scheduleDay(day)
                    .openTime(range.open())
                    .closeTime(range.close())
                    .build());
        }
        return periods;
    }

    private List<TimeRange> mergeAdjacent(List<TimeRange> sortedRanges) {
        List<TimeRange> merged = new ArrayList<>();
        for (TimeRange range : sortedRanges) {
            if (!merged.isEmpty() && !range.open().isAfter(merged.get(merged.size() - 1).close())) {
                TimeRange last = merged.get(merged.size() - 1);
                LocalTime close = last.close().isAfter(range.close()) ? last.close() : range.close();
                merged.set(merged.size() - 1, new TimeRange(last.open(), close));
            } else {
                merged.add(range);
            }
        }
        return merged;
    }

    private EffectiveSchedule toEffective(BusinessScheduleDay day) {
        if (Boolean.TRUE.equals(day.getIsClosed())) {
            return new EffectiveSchedule(true, List.of());
        }
        List<TimeRange> ranges = day.getPeriods().stream()
                .map(period -> new TimeRange(period.getOpenTime(), period.getCloseTime()))
                .sorted(Comparator.comparing(TimeRange::open))
                .toList();
        return new EffectiveSchedule(false, mergeAdjacent(new ArrayList<>(ranges)));
    }

    private BusinessScheduleResponseDto toWeeklyResponse(BusinessScheduleDay day) {
        return BusinessScheduleResponseDto.builder()
                .id(day.getId())
                .dayOfWeek(day.getDayOfWeek().name())
                .isClosed(day.getIsClosed())
                .periods(mapPeriods(day))
                .build();
    }

    private ScheduleExceptionResponseDto toExceptionResponse(BusinessScheduleDay day) {
        return ScheduleExceptionResponseDto.builder()
                .id(day.getId())
                .date(day.getSpecificDate())
                .isClosed(day.getIsClosed())
                .periods(mapPeriods(day))
                .build();
    }

    private List<SchedulePeriodDto> mapPeriods(BusinessScheduleDay day) {
        return day.getPeriods().stream()
                .sorted(Comparator.comparing(BusinessSchedulePeriod::getOpenTime))
                .map(period -> new SchedulePeriodDto(period.getOpenTime(), period.getCloseTime()))
                .toList();
    }
}

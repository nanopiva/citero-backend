package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.appointment.AvailabilityResponseDto;
import com.nanopiva.citero.entity.*;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessScheduleRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.util.BusinessTime;
import com.nanopiva.citero.util.StaffUtils;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class AvailabilityService {

    private final BusinessRepository businessRepository;
    private final BusinessScheduleRepository scheduleRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;

    public AvailabilityService(BusinessRepository businessRepository,
                               BusinessScheduleRepository scheduleRepository,
                               ServiceRepository serviceRepository,
                               StaffRepository staffRepository,
                               AppointmentRepository appointmentRepository) {
        this.businessRepository = businessRepository;
        this.scheduleRepository = scheduleRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponseDto getAvailableSlots(Long businessId, Long serviceId, Long staffId, LocalDate date) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        if (!service.getBusiness().getId().equals(businessId)) {
            throw new BadRequestException("El servicio no pertenece a este negocio.");
        }

        DailyHours hours = resolveDailyHours(business, date);

        // Si el día está cerrado, devolvemos lista vacía sin lanzar 404.
        if (hours.closed()) {
            return AvailabilityResponseDto.builder()
                    .date(date)
                    .availableSlots(new ArrayList<>())
                    .staffId(staffId)
                    .build();
        }

        List<Staff> staffToCheck = getStaffToCheck(business, service, staffId);

        if (staffToCheck.isEmpty()) {
            return AvailabilityResponseDto.builder()
                    .date(date)
                    .availableSlots(new ArrayList<>())
                    .staffId(staffId)
                    .build();
        }

        List<LocalTime> availableSlots = calculateAvailableSlots(
                business,
                hours,
                service.getDurationMinutes(),
                staffToCheck,
                date
        );

        return AvailabilityResponseDto.builder()
                .date(date)
                .availableSlots(availableSlots)
                .staffId(staffId)
                .staffName(staffId != null ? StaffUtils.displayName(staffToCheck.get(0)) : null)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isSlotAvailable(Long staffId, LocalDateTime startTime, Long serviceId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        // Un turno en el pasado nunca está disponible.
        if (startTime == null || !startTime.isAfter(BusinessTime.now(staff.getBusiness()))) {
            return false;
        }

        LocalDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());

        return !hasConflictForStaff(staff, startTime, endTime);
    }

    /**
     * Valida que un turno pueda reservarse para el empleado y servicio indicados.
     *
     * <p>Verifica las reglas de la franja (futuro, minutos exactos, grilla de 15 min y
     * horario de atención) y que el empleado no tenga un turno confirmado solapado.
     * Lanza {@link BadRequestException} con un mensaje específico cuando no se cumple.</p>
     */
    @Transactional(readOnly = true)
    public void validateSlotForBooking(Staff staff, Service service, LocalDateTime startTime) {
        validateSlotRules(staff.getBusiness(), service, startTime);

        LocalDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());
        if (hasConflictForStaff(staff, startTime, endTime)) {
            throw new BadRequestException("El horario seleccionado ya no está disponible. Por favor, elegí otro.");
        }
    }

    /**
     * Valida las reglas de la franja horaria independientemente del profesional:
     * futuro, minutos exactos, grilla de 15 minutos (desde la apertura), día abierto
     * y que el turno entre completo dentro del horario de atención.
     */
    @Transactional(readOnly = true)
    public void validateSlotRules(Business business, Service service, LocalDateTime startTime) {
        if (startTime == null) {
            throw new BadRequestException("La fecha y hora del turno es obligatoria.");
        }
        if (!startTime.isAfter(BusinessTime.now(business))) {
            throw new BadRequestException("No se puede reservar un turno en el pasado.");
        }
        if (startTime.getSecond() != 0 || startTime.getNano() != 0) {
            throw new BadRequestException("El horario debe tener minutos exactos (sin segundos).");
        }

        LocalDate date = startTime.toLocalDate();
        DailyHours hours = resolveDailyHours(business, date);

        if (hours.closed()) {
            throw new BadRequestException("El negocio no atiende ese día.");
        }

        LocalTime openTime = hours.open();
        LocalTime closeTime = hours.close();
        LocalTime slotTime = startTime.toLocalTime();

        if (slotTime.isBefore(openTime)) {
            throw new BadRequestException("El horario seleccionado está fuera del horario de atención.");
        }

        // La grilla de turnos arranca en la hora de apertura y avanza cada 15 minutos.
        long minutesFromOpen = java.time.Duration.between(openTime, slotTime).toMinutes();
        if (minutesFromOpen % 15 != 0) {
            throw new BadRequestException("El horario debe estar alineado a la grilla de 15 minutos.");
        }

        LocalDateTime slotEnd = startTime.plusMinutes(service.getDurationMinutes());
        LocalDateTime closeDateTime = LocalDateTime.of(date, closeTime);
        if (slotEnd.isAfter(closeDateTime)) {
            throw new BadRequestException("El turno no entra dentro del horario de atención.");
        }
    }

    /**
     * Elige un profesional libre para el turno solicitado ("Cualquier profesional").
     *
     * <p>Prioridad de asignación:</p>
     * <ol>
     *   <li><b>Elegibilidad:</b> solo profesionales del negocio que realizan el servicio.</li>
     *   <li><b>Disponibilidad:</b> se descartan los que ya tienen un turno {@code CONFIRMED}
     *       que se solapa con la franja pedida.</li>
     *   <li><b>Carga:</b> entre los libres, se elige el de <b>menor cantidad de turnos
     *       confirmados ese día</b> (balanceo de carga).</li>
     *   <li><b>Desempate:</b> a igual carga, el de <b>menor {@code id}</b> (criterio estable
     *       y determinista).</li>
     * </ol>
     *
     * @throws BadRequestException si no hay ningún profesional libre en esa franja.
     */
    @Transactional
    public Staff assignAvailableStaff(Business business, Service service, LocalDateTime startTime) {
        validateSlotRules(business, service, startTime);

        // Lock pesimista sobre los profesionales del negocio (ordenados por id) para que
        // la selección y el chequeo de conflictos sean atómicos ante reservas concurrentes.
        List<Staff> candidates = staffRepository.findByBusinessForUpdate(business.getId()).stream()
                .filter(candidate -> candidate.getServices().contains(service))
                .sorted(Comparator.comparing(Staff::getId))
                .toList();

        LocalDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());
        LocalDateTime dayStart = startTime.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = startTime.toLocalDate().plusDays(1).atStartOfDay();

        Staff selected = null;
        int selectedLoad = Integer.MAX_VALUE;

        for (Staff candidate : candidates) {
            List<Appointment> confirmed = appointmentRepository
                    .findByStaffAndStartTimeBetween(candidate, dayStart, dayEnd).stream()
                    .filter(appointment -> appointment.getStatus() == Appointment.AppointmentStatus.CONFIRMED)
                    .toList();

            if (hasConflict(confirmed, startTime, endTime)) {
                continue;
            }

            // Al iterar ordenados por id y usar "<" estricto, ante igual carga se conserva el de menor id.
            if (confirmed.size() < selectedLoad) {
                selected = candidate;
                selectedLoad = confirmed.size();
            }
        }

        if (selected == null) {
            throw new BadRequestException("No hay profesionales disponibles para ese horario. Por favor, elegí otro.");
        }
        return selected;
    }

    private BusinessSchedule getScheduleForDate(Business business, LocalDate date) {
        String dayOfWeek = date.getDayOfWeek().name();

        return scheduleRepository.findByBusiness(business).stream()
                .filter(schedule -> schedule.getDayOfWeek().name().equals(dayOfWeek))
                .findFirst()
                .orElse(null); // Retorna null en lugar de lanzar excepción
    }

    /**
     * Resuelve el horario de atención efectivo para un día.
     *
     * <p>Prioridad:</p>
     * <ol>
     *   <li>Si existe un {@link BusinessSchedule} para ese día, se usa (y si está marcado
     *       como cerrado, el día queda cerrado).</li>
     *   <li>Si no existe, se cae a los valores por defecto de la configuración del negocio
     *       ({@code defaultOpeningTime} / {@code defaultClosingTime}).</li>
     * </ol>
     *
     * @return un {@link DailyHours} con {@code closed = true} si no hay atención disponible.
     */
    private DailyHours resolveDailyHours(Business business, LocalDate date) {
        BusinessSchedule schedule = getScheduleForDate(business, date);

        if (schedule != null) {
            if (Boolean.TRUE.equals(schedule.getIsClosed())) {
                return new DailyHours(null, null, true);
            }
            return new DailyHours(schedule.getOpenTime(), schedule.getCloseTime(), false);
        }

        // Sin BusinessSchedule para el día: usar los valores por defecto de la config.
        BusinessConfig config = business.getConfig();
        if (config != null
                && config.getDefaultOpeningTime() != null
                && config.getDefaultClosingTime() != null) {
            return new DailyHours(config.getDefaultOpeningTime(), config.getDefaultClosingTime(), false);
        }

        // Sin horario para el día ni valores por defecto: se considera cerrado.
        return new DailyHours(null, null, true);
    }

    /**
     * Horario de atención efectivo de un día. {@code closed = true} indica que el negocio
     * no atiende (día cerrado explícitamente o sin horario configurable).
     */
    private record DailyHours(LocalTime open, LocalTime close, boolean closed) {
    }

    private List<Staff> getStaffToCheck(Business business, Service service, Long staffId) {
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

            if (!staff.getBusiness().getId().equals(business.getId())) {
                throw new BadRequestException("El empleado no pertenece a este negocio.");
            }
            if (!staff.getServices().contains(service)) {
                throw new BadRequestException("El empleado no puede realizar este servicio.");
            }

            return List.of(staff);
        } else {
            return staffRepository.findByBusiness(business).stream()
                    .filter(staff -> staff.getServices().contains(service))
                    .toList();
        }
    }

    private List<LocalTime> calculateAvailableSlots(
            Business business,
            DailyHours hours,
            int durationMinutes,
            List<Staff> staffToCheck,
            LocalDate date
    ) {
        List<LocalTime> availableSlots = new ArrayList<>();

        LocalTime openTime = hours.open();
        LocalTime closeTime = hours.close();
        LocalDateTime now = BusinessTime.now(business);

        // Se cargan una sola vez los turnos confirmados del día de todos los profesionales
        // relevantes, en lugar de consultar por cada slot y cada profesional.
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        Map<Long, List<Appointment>> confirmedByStaff = appointmentRepository
                .findByStaffInAndStartTimeBetweenAndStatus(
                        staffToCheck, dayStart, dayEnd, Appointment.AppointmentStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(appointment -> appointment.getStaff().getId()));

        LocalTime currentSlot = openTime;
        while (currentSlot.plusMinutes(durationMinutes).isBefore(closeTime) ||
                currentSlot.plusMinutes(durationMinutes).equals(closeTime)) {

            LocalDateTime slotStart = LocalDateTime.of(date, currentSlot);

            // No ofrecer horarios que ya pasaron (ni el que empieza justo ahora).
            if (slotStart.isAfter(now)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);

                boolean isAvailable = staffToCheck.stream()
                        .anyMatch(staff -> !hasConflict(
                                confirmedByStaff.getOrDefault(staff.getId(), List.of()), slotStart, slotEnd));

                if (isAvailable) {
                    availableSlots.add(currentSlot);
                }
            }

            currentSlot = currentSlot.plusMinutes(15);
        }

        return availableSlots;
    }

    /**
     * Comprueba si el empleado ya tiene un turno CONFIRMADO que se solape con el rango dado.
     */
    private boolean hasConflictForStaff(Staff staff, LocalDateTime start, LocalDateTime end) {
        LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = start.toLocalDate().plusDays(1).atStartOfDay();

        List<Appointment> existingAppointments = appointmentRepository.findByStaffAndStartTimeBetween(
                staff, dayStart, dayEnd
        );

        List<Appointment> activeAppointments = existingAppointments.stream()
                .filter(appointment -> appointment.getStatus() == Appointment.AppointmentStatus.CONFIRMED)
                .toList();

        return hasConflict(activeAppointments, start, end);
    }

    private boolean hasConflict(List<Appointment> existingAppointments, LocalDateTime newStart, LocalDateTime newEnd) {
        for (Appointment appointment : existingAppointments) {
            LocalDateTime existStart = appointment.getStartTime();
            LocalDateTime existEnd = appointment.getEndTime();

            if (newStart.isBefore(existEnd) && newEnd.isAfter(existStart)) {
                return true;
            }
        }
        return false;
    }
}
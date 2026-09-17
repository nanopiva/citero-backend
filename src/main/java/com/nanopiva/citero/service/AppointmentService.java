package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.appointment.AppointmentCreateRequestDto;
import com.nanopiva.citero.dto.appointment.AppointmentResponseDto;
import com.nanopiva.citero.dto.appointment.PublicAppointmentResponseDto;
import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.dto.business.StaffResponseDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.entity.*;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.util.BusinessTime;
import com.nanopiva.citero.util.StaffUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final ServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final UserService userService;
    private final AvailabilityService availabilityService;
    private final ReputationService reputationService;
    private final OtpService otpService;
    private final AppointmentNotificationService notificationService;
    private final String frontendUrl;

    // Rango amplio para cuando no se filtra por fecha (evita parámetros nulos en la query).
    private static final LocalDateTime RANGE_START = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2200, 1, 1, 0, 0);

    /**
     * Transiciones de estado permitidas. Los estados terminales (COMPLETED, NO_SHOW,
     * CANCELLED) no tienen salida: una vez alcanzados no se puede volver atrás por este
     * endpoint (la corrección de reputación se hace vía gestión manual, UC-22).
     */
    private static final Map<Appointment.AppointmentStatus, Set<Appointment.AppointmentStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    Appointment.AppointmentStatus.CONFIRMED, Set.of(
                            Appointment.AppointmentStatus.COMPLETED,
                            Appointment.AppointmentStatus.NO_SHOW,
                            Appointment.AppointmentStatus.CANCELLED
                    )
            );

    public AppointmentService(AppointmentRepository appointmentRepository,
                              UserRepository userRepository,
                              StaffRepository staffRepository,
                              ServiceRepository serviceRepository,
                              BusinessRepository businessRepository,
                              UserService userService,
                              AvailabilityService availabilityService,
                              ReputationService reputationService,
                              OtpService otpService,
                              AppointmentNotificationService notificationService,
                              @Value("${citero.frontend.url}") String frontendUrl) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.businessRepository = businessRepository;
        this.userService = userService;
        this.availabilityService = availabilityService;
        this.reputationService = reputationService;
        this.otpService = otpService;
        this.notificationService = notificationService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public AppointmentResponseDto createAppointment(Long authenticatedUserId, AppointmentCreateRequestDto dto) {
        com.nanopiva.citero.entity.Service service = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado."));

        Business business = service.getBusiness();
        BusinessConfig config = business.getConfig();

        // Resolver el profesional: el elegido por el cliente o uno libre ("Cualquier profesional").
        Staff staff;
        if (dto.getStaffId() != null) {
            // Lock pesimista sobre el profesional para serializar reservas concurrentes (evita doble reserva).
            staff = staffRepository.findByIdForUpdate(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));

            if (!staff.getBusiness().getId().equals(business.getId())) {
                throw new BadRequestException("El empleado seleccionado no pertenece al negocio del servicio.");
            }
            if (!staff.getServices().contains(service)) {
                throw new BadRequestException("El empleado seleccionado no realiza este servicio.");
            }

            // Valida futuro, grilla de 15 min, horario de atención y solapamiento.
            availabilityService.validateSlotForBooking(staff, service, dto.getStartTime());
        } else {
            // "Cualquier profesional": el backend elige un profesional libre para la franja.
            staff = availabilityService.assignAvailableStaff(business, service, dto.getStartTime());
        }

        User client = resolveClient(authenticatedUserId, dto, config);

        if (reputationService.isClientBlocked(client.getId(), business.getId())) {
            throw new BadRequestException("No puedes reservar en este negocio debido a ausencias o cancelaciones previas.");
        }

        Appointment appointment = Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(dto.getStartTime())
                .endTime(dto.getStartTime().plusMinutes(service.getDurationMinutes()))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Disparar notificaciones por email (asíncrono, no bloquea la respuesta HTTP)
        notificationService.sendAppointmentConfirmation(savedAppointment, frontendUrl);

        return mapToResponseDto(savedAppointment);
    }

    @Transactional
    public AppointmentResponseDto cancelByClient(Long appointmentId, Long clientId) {
        Appointment appointment = getAppointmentForUpdate(appointmentId);
        if (!appointment.getClient().getId().equals(clientId)) {
            throw new ForbiddenException("No tienes permiso para cancelar este turno.");
        }
        validateCancellable(appointment);
        BusinessConfig config = appointment.getStaff().getBusiness().getConfig();
        long hoursUntilStart = Duration.between(
                BusinessTime.now(appointment.getStaff().getBusiness()), appointment.getStartTime()).toHours();
        if (hoursUntilStart < config.getCancellationToleranceHours()) {
            reputationService.applyStrike(clientId, appointment.getStaff().getBusiness().getId(), "Cancelacion tardia");
        }
        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Disparar notificaciones de cancelación
        notificationService.sendAppointmentCancellation(savedAppointment, frontendUrl);

        return mapToResponseDto(savedAppointment);
    }


    @Transactional
    public AppointmentResponseDto cancelByBusiness(Long appointmentId, Long ownerId) {
        Appointment appointment = getAppointmentAndValidateOwner(appointmentId, ownerId);

        // Idempotencia: si ya está cancelado, no se re-notifica ni se vuelve a guardar.
        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            return mapToResponseDto(appointment);
        }
        if (!canTransition(appointment.getStatus(), Appointment.AppointmentStatus.CANCELLED)) {
            throw new BadRequestException("No se puede cancelar un turno en estado " + appointment.getStatus() + ".");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Notificar al cliente que el negocio canceló el turno (no se notifica al dueño, que fue quien canceló)
        notificationService.sendAppointmentCancellationByBusiness(savedAppointment, frontendUrl);

        return mapToResponseDto(savedAppointment);
    }

    @Transactional
    public AppointmentResponseDto updateStatus(Long appointmentId, Long ownerId, Appointment.AppointmentStatus newStatus) {
        if (newStatus == null) {
            throw new BadRequestException("El nuevo estado es obligatorio.");
        }
        if (newStatus == Appointment.AppointmentStatus.CONFIRMED || newStatus == Appointment.AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Usa los endpoints específicos para confirmar o cancelar.");
        }

        Appointment appointment = getAppointmentAndValidateOwner(appointmentId, ownerId);

        // Idempotencia: si el turno ya está en ese estado, no se aplica ningún efecto
        // secundario (evita aplicar un strike repetido por NO_SHOW).
        if (appointment.getStatus() == newStatus) {
            return mapToResponseDto(appointment);
        }

        if (!canTransition(appointment.getStatus(), newStatus)) {
            throw new BadRequestException(
                    "No se puede cambiar el estado de " + appointment.getStatus() + " a " + newStatus + ".");
        }

        if (newStatus == Appointment.AppointmentStatus.NO_SHOW) {
            reputationService.applyStrike(
                    appointment.getClient().getId(),
                    appointment.getStaff().getBusiness().getId(),
                    "No se presento al turno"
            );
        }

        appointment.setStatus(newStatus);
        return mapToResponseDto(appointmentRepository.save(appointment));
    }

    /**
     * Devuelve los detalles básicos de un turno para mostrar en la página pública de gestión.
     * No expone información sensible del cliente.
     */
    @Transactional(readOnly = true)
    public PublicAppointmentResponseDto getPublicAppointmentDetails(Long appointmentId, Long viewerUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado."));

        Business business = appointment.getStaff().getBusiness();
        String staffDisplayName = StaffUtils.displayName(appointment.getStaff());
        boolean ownedByViewer = viewerUserId != null
                && appointment.getClient().getId().equals(viewerUserId);

        return PublicAppointmentResponseDto.builder()
                .id(appointment.getId())
                .businessName(business.getName())
                .serviceName(appointment.getService().getName())
                .staffName(staffDisplayName)
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().name())
                .address(business.getAddress() != null ? business.getAddress() : "Dirección no disponible")
                .ownedByViewer(ownedByViewer)
                .build();
    }

    /**
     * Envía un OTP al email del cliente para autorizar la cancelación de un turno.
     * Valida que el email coincida con el cliente del turno antes de enviar.
     */
    public void sendCancellationOtp(Long appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado."));

        if (!appointment.getClient().getEmail().equalsIgnoreCase(email)) {
            throw new BadRequestException("El email no coincide con el cliente del turno.");
        }

        validateCancellable(appointment);

        otpService.generateAndSendOtp(email, OtpService.PURPOSE_CANCELLATION_VERIFICATION);

    }

    /**
     * Cancela un turno validando la identidad del cliente mediante OTP.
     * Aplica las mismas reglas de negocio (strikes) que cancelByClient.
     */
    @Transactional
    public AppointmentResponseDto cancelByGuest(Long appointmentId, String email, String otpCode) {
        Appointment appointment = getAppointmentForUpdate(appointmentId);

        if (!appointment.getClient().getEmail().equalsIgnoreCase(email)) {
            throw new BadRequestException("El email no coincide con el cliente del turno.");
        }

        validateCancellable(appointment);

        // Validar OTP con propósito específico de cancelación
        otpService.verifyOtp(email, otpCode, OtpService.PURPOSE_CANCELLATION_VERIFICATION);

        // Aplicar strike si la cancelación es tardía (misma lógica que cancelByClient)
        BusinessConfig config = appointment.getStaff().getBusiness().getConfig();
        long hoursUntilStart = Duration.between(
                BusinessTime.now(appointment.getStaff().getBusiness()), appointment.getStartTime()).toHours();
        if (hoursUntilStart < config.getCancellationToleranceHours()) {
            reputationService.applyStrike(
                    appointment.getClient().getId(),
                    appointment.getStaff().getBusiness().getId(),
                    "Cancelacion tardia"
            );
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        // Disparar notificaciones de cancelación
        notificationService.sendAppointmentCancellation(savedAppointment, frontendUrl);

        return mapToResponseDto(savedAppointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDto> getAppointmentsByClient(Long clientId, Pageable pageable) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado."));
        return appointmentRepository.findByClient(client, pageable).map(this::mapToResponseDto);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDto> getAppointments(Long userId, Long businessId, Long staffId,
                                                        LocalDate date, Appointment.AppointmentStatus status,
                                                        Pageable pageable) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        if (!business.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("No tienes permiso para ver la agenda de este negocio.");
        }
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));
            if (!staff.getBusiness().getId().equals(businessId)) {
                throw new BadRequestException("El empleado no pertenece a este negocio.");
            }
        }
        LocalDateTime start = date != null ? date.atStartOfDay() : RANGE_START;
        LocalDateTime end = date != null ? date.plusDays(1).atStartOfDay() : RANGE_END;
        return appointmentRepository.searchByBusiness(
                        businessId, staffId != null, staffId, start, end, status != null, status, pageable)
                .map(this::mapToResponseDto);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDto> getAppointmentsForStaff(Long userId, Long businessId, LocalDate date,
                                                                Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        Staff staff = staffRepository.findByUserAndBusiness(user, business)
                .orElseThrow(() -> new BadRequestException("El usuario no está registrado como empleado en este negocio."));
        LocalDateTime start = date != null ? date.atStartOfDay() : RANGE_START;
        LocalDateTime end = date != null ? date.plusDays(1).atStartOfDay() : RANGE_END;
        return appointmentRepository.searchByBusiness(
                        businessId, true, staff.getId(), start, end, false, null, pageable)
                .map(this::mapToResponseDto);
    }

    /**
     * Valida que un turno pueda ser cancelado (no haya pasado y esté confirmado).
     * Lógica compartida entre cancelByClient y cancelByGuest.
     */
    private void validateCancellable(Appointment appointment) {
        if (appointment.getStartTime().isBefore(BusinessTime.now(appointment.getStaff().getBusiness()))) {
            throw new BadRequestException("No puedes cancelar un turno que ya ha pasado.");
        }
        if (appointment.getStatus() != Appointment.AppointmentStatus.CONFIRMED) {
            throw new BadRequestException("Este turno ya ha sido cancelado o completado.");
        }
    }

    private User resolveClient(Long authenticatedUserId, AppointmentCreateRequestDto dto, BusinessConfig config) {
        if (authenticatedUserId != null) {
            return userService.getUserEntityById(authenticatedUserId);
        }
        if (config.getReservationMode() == BusinessConfig.ReservationMode.PUBLIC) {
            if (dto.getGuestEmail() == null || dto.getGuestEmail().isBlank()) {
                throw new BadRequestException("El email es obligatorio para reservar como invitado.");
            }
            return userService.findOrCreateGuestUser(dto.getGuestEmail(), dto.getGuestPhone());
        }
        if (config.getReservationMode() == BusinessConfig.ReservationMode.AUTHENTICATED) {
            if (dto.getGuestEmail() == null || dto.getGuestEmail().isBlank()) {
                throw new BadRequestException("El email es obligatorio para verificar tu identidad.");
            }
            if (dto.getOtpCode() == null || dto.getOtpCode().isBlank()) {
                throw new BadRequestException("Debes ingresar el código OTP enviado a tu email para reservar en este negocio.");
            }
            otpService.verifyOtp(dto.getGuestEmail(), dto.getOtpCode(), OtpService.PURPOSE_GUEST_VERIFICATION);
            return userService.findOrCreateGuestUser(dto.getGuestEmail(), dto.getGuestPhone());
        }
        throw new BadRequestException("Modo de reserva inválido.");
    }

    private Appointment getAppointmentAndValidateOwner(Long appointmentId, Long ownerId) {
        Appointment appointment = getAppointmentForUpdate(appointmentId);
        if (!appointment.getStaff().getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este turno.");
        }
        return appointment;
    }

    /**
     * Carga el turno adquiriendo un lock pesimista (SELECT ... FOR UPDATE) para que los
     * cambios de estado sobre un mismo turno se serialicen y no se dupliquen efectos
     * (p. ej. dos strikes por NO_SHOW ante dobles clics).
     */
    private Appointment getAppointmentForUpdate(Long appointmentId) {
        return appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado."));
    }

    private boolean canTransition(Appointment.AppointmentStatus from, Appointment.AppointmentStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private AppointmentResponseDto mapToResponseDto(Appointment appointment) {
        return AppointmentResponseDto.builder()
                .id(appointment.getId())
                .businessName(appointment.getService().getBusiness().getName())
                .client(mapUserToDto(appointment.getClient()))
                .staff(mapStaffToDto(appointment.getStaff()))
                .service(mapServiceToDto(appointment.getService()))
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().name())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    private UserResponseDto mapUserToDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private StaffResponseDto mapStaffToDto(Staff staff) {
        return StaffResponseDto.builder()
                .id(staff.getId())
                .customName(staff.getCustomName())
                .userEmail(StaffUtils.email(staff))
                .hasClaimedAccount(StaffUtils.hasClaimedAccount(staff))
                .build();
    }

    private ServiceResponseDto mapServiceToDto(com.nanopiva.citero.entity.Service service) {
        return ServiceResponseDto.builder()
                .id(service.getId())
                .name(service.getName())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .build();
    }
}
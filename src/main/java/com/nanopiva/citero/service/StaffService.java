package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.StaffCreateRequestDto;
import com.nanopiva.citero.dto.business.StaffResponseDto;
import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Service;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.util.StaffUtils;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class StaffService {

    private static final int INVITATION_VALID_DAYS = 7;

    private final StaffRepository staffRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final StaffNotificationService staffNotificationService;
    private final AppointmentRepository appointmentRepository;

    public StaffService(StaffRepository staffRepository,
                        BusinessRepository businessRepository,
                        UserRepository userRepository,
                        ServiceRepository serviceRepository,
                        StaffNotificationService staffNotificationService,
                        AppointmentRepository appointmentRepository) {
        this.staffRepository = staffRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.staffNotificationService = staffNotificationService;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public StaffResponseDto createStaff(Long businessId, Long ownerId, StaffCreateRequestDto requestDto) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para agregar empleados a este negocio.");
        }

        Staff staff = new Staff();
        staff.setBusiness(business);
        staff.setCustomName(requestDto.getCustomName());

        Optional<User> existingUser = userRepository.findByEmail(requestDto.getEmail());
        boolean isRegistered = existingUser.isPresent();
        // El dueño que se agrega a sí mismo no necesita invitación ni confirmación:
        // ya es miembro del negocio y su cuenta está activa.
        boolean isOwnerSelf = isRegistered
                && business.getOwner().getId().equals(existingUser.get().getId());

        String invitationToken = null;
        if (isRegistered) {
            if (staffRepository.existsByUserAndBusiness(existingUser.get(), business)) {
                throw new DuplicateResourceException("El usuario ya está registrado como empleado en este local.");
            }
            staff.setUser(existingUser.get());
        } else {
            if (staffRepository.existsByContactEmailAndBusiness(requestDto.getEmail(), business)) {
                throw new DuplicateResourceException("Ya existe un perfil pendiente con este correo en el local.");
            }
            staff.setContactEmail(requestDto.getEmail());
            invitationToken = UUID.randomUUID().toString();
            staff.setInvitationToken(invitationToken);
            staff.setInvitationExpiresAt(LocalDateTime.now().plusDays(INVITATION_VALID_DAYS));
        }

        if (requestDto.getServiceIds() != null && !requestDto.getServiceIds().isEmpty()) {
            Set<Service> services = getServicesByIds(business, requestDto.getServiceIds());
            staff.setServices(services);
        }

        Staff savedStaff = staffRepository.save(staff);

        // Notificación asíncrona (no bloquea la respuesta).
        // Se omite si el dueño se agrega a sí mismo: no hay nada que invitar ni confirmar.
        if (!isOwnerSelf) {
            staffNotificationService.sendStaffInvitation(
                    requestDto.getEmail(),
                    requestDto.getCustomName(),
                    business.getName(),
                    isRegistered,
                    invitationToken
            );
        }

        return mapToResponseDto(savedStaff);
    }

    /**
     * Alta del dueño como profesional de su propio negocio.
     * No envía invitación ni requiere confirmación: el dueño ya es miembro activo.
     */
    @Transactional
    public StaffResponseDto addOwnerAsStaff(Long businessId, Long ownerId, StaffCreateRequestDto requestDto) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para agregarte a este negocio.");
        }

        User owner = business.getOwner();
        if (staffRepository.existsByUserAndBusiness(owner, business)) {
            throw new DuplicateResourceException("Ya formás parte del equipo de este negocio.");
        }

        Staff staff = new Staff();
        staff.setBusiness(business);
        staff.setCustomName(requestDto.getCustomName());
        staff.setUser(owner);

        if (requestDto.getServiceIds() != null && !requestDto.getServiceIds().isEmpty()) {
            staff.setServices(getServicesByIds(business, requestDto.getServiceIds()));
        }

        return mapToResponseDto(staffRepository.save(staff));
    }

    @Transactional(readOnly = true)
    public List<StaffResponseDto> getStaffByBusinessId(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        return staffRepository.findByBusiness(business).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional
    public StaffResponseDto updateStaff(Long staffId, Long ownerId, StaffCreateRequestDto requestDto) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

        if (!staff.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este empleado.");
        }

        if (requestDto.getCustomName() != null) {
            staff.setCustomName(requestDto.getCustomName());
        }

        if (requestDto.getServiceIds() != null) {
            Set<Service> services = getServicesByIds(staff.getBusiness(), requestDto.getServiceIds());
            staff.setServices(services);
        }

        Staff updatedStaff = staffRepository.save(staff);
        return mapToResponseDto(updatedStaff);
    }

    /**
     * Reenvía la invitación a un profesional que todavía no creó su cuenta, regenerando el
     * token (por si el anterior venció).
     */
    @Transactional
    public void resendInvitation(Long staffId, Long ownerId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

        if (!staff.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para gestionar este empleado.");
        }
        if (staff.getUser() != null) {
            throw new BadRequestException("Este profesional ya tiene una cuenta vinculada.");
        }

        String token = UUID.randomUUID().toString();
        staff.setInvitationToken(token);
        staff.setInvitationExpiresAt(LocalDateTime.now().plusDays(INVITATION_VALID_DAYS));
        staffRepository.save(staff);

        staffNotificationService.sendStaffInvitation(
                staff.getContactEmail(), staff.getCustomName(), staff.getBusiness().getName(), false, token);
    }

    @Transactional
    public void deleteStaff(Long staffId, Long ownerId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

        if (!staff.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para eliminar este empleado.");
        }

        // Borrado explícito de los turnos del profesional (además del cascade).
        appointmentRepository.deleteAll(appointmentRepository.findByStaff(staff));
        staffRepository.delete(staff);
    }

    /**
     * Un profesional se elimina a sí mismo del equipo de un negocio (siempre que
     * exista un perfil de staff vinculado a su usuario en ese negocio).
     */
    @Transactional
    public void leaveStaff(Long businessId, Long userId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        Staff staff = staffRepository.findByUserAndBusiness(user, business)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No formás parte del equipo de este negocio."));

        appointmentRepository.deleteAll(appointmentRepository.findByStaff(staff));
        staffRepository.delete(staff);
    }

    @Transactional
    public StaffResponseDto assignServicesToStaff(Long staffId, Long ownerId, Set<Long> serviceIds) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));

        if (!staff.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para asignar servicios a este empleado.");
        }

        Set<Service> services = getServicesByIds(staff.getBusiness(), serviceIds);
        staff.setServices(services);
        Staff updatedStaff = staffRepository.save(staff);
        return mapToResponseDto(updatedStaff);
    }

    private Set<Service> getServicesByIds(Business business, Set<Long> serviceIds) {
        return serviceRepository.findAllById(serviceIds).stream()
                .peek(service -> {
                    if (!service.getBusiness().getId().equals(business.getId())) {
                        throw new BadRequestException("El servicio con ID " + service.getId() + " no pertenece a este negocio.");
                    }
                })
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Staff getStaffEntityById(Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + staffId));
    }

    private StaffResponseDto mapToResponseDto(Staff staff) {
        return StaffResponseDto.builder()
                .id(staff.getId())
                .customName(staff.getCustomName())
                .userEmail(StaffUtils.email(staff))
                .hasClaimedAccount(StaffUtils.hasClaimedAccount(staff))
                .services(mapServicesToDto(staff.getServices()))
                .build();
    }

    private Set<ServiceResponseDto> mapServicesToDto(Set<Service> services) {
        if (services == null || services.isEmpty()) {
            return new HashSet<>();
        }
        return services.stream()
                .map(service -> ServiceResponseDto.builder()
                        .id(service.getId())
                        .name(service.getName())
                        .durationMinutes(service.getDurationMinutes())
                        .price(service.getPrice())
                        .build())
                .collect(Collectors.toSet());
    }
}
package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessConfigRequestDto;
import com.nanopiva.citero.dto.business.BusinessConfigResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
public class BusinessConfigService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    public BusinessConfigService(BusinessRepository businessRepository,
                                 UserRepository userRepository,
                                 StaffRepository staffRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
    }

    /** Config del negocio: completa para dueño/staff; para el resto sólo el modo de reserva. */
    @Transactional(readOnly = true)
    public BusinessConfigResponseDto getConfigByBusinessId(Long businessId, Long viewerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        BusinessConfig config = business.getConfig();
        if (config == null) {
            throw new ResourceNotFoundException("Configuración no encontrada para el negocio con ID: " + businessId);
        }
        return mapToResponseDto(config, hasManagementAccess(business, viewerId));
    }

    /** Acceso completo a la config: dueño o miembro del staff. */
    private boolean hasManagementAccess(Business business, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (business.getOwner().getId().equals(viewerId)) {
            return true;
        }
        return userRepository.findById(viewerId)
                .flatMap(user -> staffRepository.findByUserAndBusiness(user, business))
                .isPresent();
    }

    @Transactional
    public BusinessConfigResponseDto updateConfig(Long businessId, Long ownerId, BusinessConfigRequestDto requestDto) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar la configuración de este negocio.");
        }

        BusinessConfig config = business.getConfig();
        if (config == null) {
            throw new ResourceNotFoundException("Configuración no encontrada para el negocio con ID: " + businessId);
        }

        // Semántica PATCH: cada campo se aplica sólo si viene distinto de null.
        if (requestDto.getReservationMode() != null) {
            try {
                config.setReservationMode(BusinessConfig.ReservationMode.valueOf(
                        requestDto.getReservationMode().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Modo de reserva inválido. Debe ser PUBLIC o AUTHENTICATED.");
            }
        }
        if (requestDto.getCancellationToleranceHours() != null) {
            config.setCancellationToleranceHours(requestDto.getCancellationToleranceHours());
        }
        if (requestDto.getStaffCanViewFullAgenda() != null) {
            config.setStaffCanViewFullAgenda(requestDto.getStaffCanViewFullAgenda());
        }

        // Validación de apertura/cierre combinando lo enviado con lo existente.
        LocalTime opening = requestDto.getDefaultOpeningTime() != null
                ? requestDto.getDefaultOpeningTime() : config.getDefaultOpeningTime();
        LocalTime closing = requestDto.getDefaultClosingTime() != null
                ? requestDto.getDefaultClosingTime() : config.getDefaultClosingTime();
        if (opening != null && closing != null && !opening.isBefore(closing)) {
            throw new BadRequestException("La hora de apertura debe ser anterior a la de cierre.");
        }
        config.setDefaultOpeningTime(opening);
        config.setDefaultClosingTime(closing);

        if (requestDto.getEnableReminders() != null) {
            config.setEnableReminders(requestDto.getEnableReminders());
        }
        if (requestDto.getReminder24hEnabled() != null) {
            config.setReminder24hEnabled(requestDto.getReminder24hEnabled());
        }
        if (requestDto.getReminder2hEnabled() != null) {
            config.setReminder2hEnabled(requestDto.getReminder2hEnabled());
        }

        // El cascade persiste los cambios en la configuración al guardar el negocio.
        businessRepository.save(business);
        return mapToResponseDto(config, true);
    }

    @Transactional(readOnly = true)
    public BusinessConfig getConfigEntityByBusinessId(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        BusinessConfig config = business.getConfig();
        if (config == null) {
            throw new ResourceNotFoundException("Configuración no encontrada para el negocio con ID: " + businessId);
        }
        return config;
    }

    private BusinessConfigResponseDto mapToResponseDto(BusinessConfig config, boolean full) {
        BusinessConfigResponseDto.BusinessConfigResponseDtoBuilder builder = BusinessConfigResponseDto.builder()
                .reservationMode(config.getReservationMode().name());

        if (full) {
            builder.cancellationToleranceHours(config.getCancellationToleranceHours())
                    .staffCanViewFullAgenda(config.getStaffCanViewFullAgenda())
                    .defaultOpeningTime(config.getDefaultOpeningTime())
                    .defaultClosingTime(config.getDefaultClosingTime())
                    .enableReminders(config.getEnableReminders())
                    .reminder24hEnabled(config.getReminder24hEnabled())
                    .reminder2hEnabled(config.getReminder2hEnabled());
        }
        return builder.build();
    }
}
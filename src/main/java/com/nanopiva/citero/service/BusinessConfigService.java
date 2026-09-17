package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessConfigRequestDto;
import com.nanopiva.citero.dto.business.BusinessConfigResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessConfigService {

    private final BusinessRepository businessRepository;

    public BusinessConfigService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Transactional(readOnly = true)
    public BusinessConfigResponseDto getConfigByBusinessId(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
        BusinessConfig config = business.getConfig();
        if (config == null) {
            throw new ResourceNotFoundException("Configuración no encontrada para el negocio con ID: " + businessId);
        }
        return mapToResponseDto(config);
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

        try {
            config.setReservationMode(BusinessConfig.ReservationMode.valueOf(requestDto.getReservationMode().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Modo de reserva inválido. Debe ser PUBLIC o AUTHENTICATED.");
        }

        config.setCancellationToleranceHours(requestDto.getCancellationToleranceHours());
        config.setEnablePenalties(requestDto.getEnablePenalties());
        config.setMaxStrikes(requestDto.getMaxStrikes());
        config.setDefaultOpeningTime(requestDto.getDefaultOpeningTime());
        config.setDefaultClosingTime(requestDto.getDefaultClosingTime());

        config.setEnableReminders(requestDto.getEnableReminders());
        config.setReminder24hEnabled(requestDto.getReminder24hEnabled());
        config.setReminder2hEnabled(requestDto.getReminder2hEnabled());

        // El cascade persiste los cambios en la configuración al guardar el negocio.
        businessRepository.save(business);
        return mapToResponseDto(config);
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

    private BusinessConfigResponseDto mapToResponseDto(BusinessConfig config) {
        return BusinessConfigResponseDto.builder()
                .reservationMode(config.getReservationMode().name())
                .cancellationToleranceHours(config.getCancellationToleranceHours())
                .enablePenalties(config.getEnablePenalties())
                .maxStrikes(config.getMaxStrikes())
                .defaultOpeningTime(config.getDefaultOpeningTime())
                .defaultClosingTime(config.getDefaultClosingTime())
                .enableReminders(config.getEnableReminders())
                .reminder24hEnabled(config.getReminder24hEnabled())
                .reminder2hEnabled(config.getReminder2hEnabled())
                .build();
    }
}
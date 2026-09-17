package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.ServiceCreateRequestDto;
import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.dto.business.ServiceUpdateDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Service;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ConflictException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;

    public ServiceCatalogService(ServiceRepository serviceRepository,
                                 BusinessRepository businessRepository,
                                 AppointmentRepository appointmentRepository) {
        this.serviceRepository = serviceRepository;
        this.businessRepository = businessRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * UC-08: Crea un nuevo servicio para un negocio.
     */
    @Transactional
    public ServiceResponseDto createService(Long businessId, Long ownerId, ServiceCreateRequestDto requestDto) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este negocio.");
        }

        Service service = new Service();
        service.setBusiness(business);
        service.setName(requestDto.getName());
        service.setDurationMinutes(requestDto.getDurationMinutes());
        service.setPrice(requestDto.getPrice());

        Service savedService = serviceRepository.save(service);
        return mapToResponseDto(savedService);
    }

    /**
     * Obtiene todos los servicios de un negocio.
     */
    @Transactional(readOnly = true)
    public List<ServiceResponseDto> getServicesByBusinessId(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        List<Service> services = serviceRepository.findByBusiness(business);
        return services.stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    /**
     * UC-08: Actualiza un servicio existente.
     */
    @Transactional
    public ServiceResponseDto updateService(Long serviceId, Long ownerId, ServiceUpdateDto updateDto) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        if (!service.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este servicio.");
        }

        // Actualización parcial
        if (updateDto.getName() != null && !updateDto.getName().isBlank()) {
            service.setName(updateDto.getName());
        }
        if (updateDto.getDurationMinutes() != null) {
            service.setDurationMinutes(updateDto.getDurationMinutes());
        }
        if (updateDto.getPrice() != null) {
            service.setPrice(updateDto.getPrice());
        }

        Service updatedService = serviceRepository.save(service);
        return mapToResponseDto(updatedService);
    }

    /**
     * UC-08: Elimina un servicio.
     */
    @Transactional
    public void deleteService(Long serviceId, Long ownerId) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        if (!service.getBusiness().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para eliminar este servicio.");
        }

        if (appointmentRepository.existsByService(service)) {
            throw new ConflictException("No se puede eliminar un servicio con turnos asociados.");
        }

        serviceRepository.delete(service);
    }

    private ServiceResponseDto mapToResponseDto(Service service) {
        return ServiceResponseDto.builder()
                .id(service.getId())
                .name(service.getName())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .build();
    }
}
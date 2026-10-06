package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessClientResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessClient;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessClientRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de clientes de un negocio y su bloqueo manual (lista negra). Sin strikes
 * automáticos: el dueño decide a quién bloquea o desbloquea, con un motivo.
 */
@Service
public class BusinessClientService {

    private final BusinessClientRepository businessClientRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    public BusinessClientService(BusinessClientRepository businessClientRepository,
                                 BusinessRepository businessRepository,
                                 UserRepository userRepository) {
        this.businessClientRepository = businessClientRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------------
    // Acciones manuales del dueño
    // ---------------------------------------------------------------------

    /** Bloquea al cliente en el negocio, con un motivo. */
    @Transactional
    public BusinessClientResponseDto blockClient(Long clientId, Long businessId, Long ownerId, String reason) {
        Business business = loadOwnedBusiness(businessId, ownerId);
        User client = loadClient(clientId);
        BusinessClient businessClient = lockOrCreate(client, business);
        businessClient.setIsBlocked(true);
        businessClient.setBlockReason(normalizeReason(reason));
        return mapToResponseDto(businessClientRepository.save(businessClient));
    }

    /** Levanta el bloqueo del cliente. */
    @Transactional
    public BusinessClientResponseDto unblockClient(Long clientId, Long businessId, Long ownerId) {
        Business business = loadOwnedBusiness(businessId, ownerId);
        User client = loadClient(clientId);
        BusinessClient businessClient = lockOrCreate(client, business);
        businessClient.setIsBlocked(false);
        businessClient.setBlockReason(null);
        return mapToResponseDto(businessClientRepository.save(businessClient));
    }

    // ---------------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------------

    /** Obtiene el registro validando que el solicitante sea el dueño del negocio.
     *  Puede crear el registro on-demand, por eso no es de solo lectura. */
    @Transactional
    public BusinessClientResponseDto getBusinessClient(Long clientId, Long businessId, Long ownerId) {
        Business business = loadOwnedBusiness(businessId, ownerId);
        User client = loadClient(clientId);
        BusinessClient businessClient = businessClientRepository.findByClientAndBusiness(client, business)
                .orElseGet(() -> getOrCreateBusinessClient(client, business));
        return mapToResponseDto(businessClient);
    }

    /** Indica si un cliente está bloqueado en un negocio. */
    @Transactional(readOnly = true)
    public boolean isClientBlocked(Long clientId, Long businessId) {
        User client = loadClient(clientId);
        Business business = loadBusiness(businessId);
        return businessClientRepository.findByClientAndBusiness(client, business)
                .map(BusinessClient::getIsBlocked)
                .orElse(false);
    }

    /** Lista los clientes del negocio. Requiere ser dueño. */
    @Transactional(readOnly = true)
    public Page<BusinessClientResponseDto> getBusinessClients(Long businessId, Long ownerId, Pageable pageable) {
        Business business = loadOwnedBusiness(businessId, ownerId);
        return businessClientRepository.findByBusiness(business, pageable).map(this::mapToResponseDto);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private BusinessClient lockOrCreate(User client, Business business) {
        return businessClientRepository.findByClientAndBusinessForUpdate(client.getId(), business.getId())
                .orElseGet(() -> getOrCreateBusinessClient(client, business));
    }

    private BusinessClient getOrCreateBusinessClient(User client, Business business) {
        return businessClientRepository.findByClientAndBusiness(client, business)
                .orElseGet(() -> {
                    BusinessClient created = new BusinessClient();
                    created.setClient(client);
                    created.setBusiness(business);
                    created.setIsBlocked(false);
                    return businessClientRepository.save(created);
                });
    }

    private Business loadOwnedBusiness(Long businessId, Long ownerId) {
        Business business = loadBusiness(businessId);
        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para gestionar los clientes de este negocio.");
        }
        return business;
    }

    private User loadClient(Long clientId) {
        return userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));
    }

    private Business loadBusiness(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private BusinessClientResponseDto mapToResponseDto(BusinessClient businessClient) {
        return BusinessClientResponseDto.builder()
                .id(businessClient.getId())
                .clientId(businessClient.getClient().getId())
                .clientEmail(businessClient.getClient().getEmail())
                .isBlocked(businessClient.getIsBlocked())
                .blockReason(businessClient.getBlockReason())
                .lastUpdated(businessClient.getLastUpdated())
                .build();
    }
}

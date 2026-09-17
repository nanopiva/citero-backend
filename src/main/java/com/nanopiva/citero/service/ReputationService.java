package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.ClientReputationResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReputationService {

    private final ClientReputationRepository reputationRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessConfigService businessConfigService;

    public ReputationService(ClientReputationRepository reputationRepository,
                             BusinessRepository businessRepository,
                             UserRepository userRepository,
                             BusinessConfigService businessConfigService) {
        this.reputationRepository = reputationRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.businessConfigService = businessConfigService;
    }

    /**
     * UC-19 y UC-20: Aplica un strike a un cliente en un negocio especifico.
     * Si el cliente alcanza el maximo de strikes, se bloquea automaticamente (UC-21).
     */
    @Transactional
    public ClientReputation applyStrike(Long clientId, Long businessId, String reason) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        BusinessConfig config = businessConfigService.getConfigEntityByBusinessId(businessId);

        if (!config.getEnablePenalties()) {
            return getOrCreateReputation(client, business);
        }

        // Lock pesimista para serializar incrementos concurrentes del mismo cliente
        // y evitar el lost update.
        ClientReputation reputation = reputationRepository
                .findByClientAndBusinessForUpdate(client.getId(), business.getId())
                .orElseGet(() -> getOrCreateReputation(client, business));

        reputation.setStrikeCount(reputation.getStrikeCount() + 1);

        if (reputation.getStrikeCount() >= config.getMaxStrikes()) {
            reputation.setIsBlocked(true);
        }

        return reputationRepository.save(reputation);
    }

    /**
     * Obtiene la reputacion de un cliente en un negocio especifico (sin validar ownership).
     * Usado internamente por otros services.
     */
    @Transactional(readOnly = true)
    public ClientReputation getReputation(Long clientId, Long businessId) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        return reputationRepository.findByClientAndBusiness(client, business)
                .orElseGet(() -> getOrCreateReputation(client, business));
    }

    /**
     * Obtiene la reputacion de un cliente validando que el solicitante sea el dueno del negocio.
     */
    @Transactional(readOnly = true)
    public ClientReputationResponseDto getReputationWithValidation(Long clientId, Long businessId, Long ownerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para ver la reputacion de clientes en este negocio.");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));

        ClientReputation reputation = reputationRepository.findByClientAndBusiness(client, business)
                .orElseGet(() -> getOrCreateReputation(client, business));

        return mapToResponseDto(reputation);
    }

    /**
     * Verifica si un cliente esta bloqueado en un negocio especifico.
     * Usado por AppointmentService antes de crear un turno (UC-14).
     */
    @Transactional(readOnly = true)
    public boolean isClientBlocked(Long clientId, Long businessId) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        return reputationRepository.findByClientAndBusiness(client, business)
                .map(ClientReputation::getIsBlocked)
                .orElse(false); // Si no hay registro, no esta bloqueado
    }

    /**
     * UC-22: Resetea los strikes de un cliente (pero no lo desbloquea).
     */
    @Transactional
    public ClientReputationResponseDto resetStrikes(Long clientId, Long businessId, Long ownerId) {
        ClientReputation reputation = getReputationWithOwnerValidation(clientId, businessId, ownerId);
        reputation.setStrikeCount(0);
        return mapToResponseDto(reputationRepository.save(reputation));
    }

    /**
     * UC-22: Desbloquea manualmente a un cliente y resetea sus strikes.
     */
    @Transactional
    public ClientReputationResponseDto unblockClient(Long clientId, Long businessId, Long ownerId) {
        ClientReputation reputation = getReputationWithOwnerValidation(clientId, businessId, ownerId);
        reputation.setIsBlocked(false);
        reputation.setStrikeCount(0); // Al desbloquear, tambien reseteamos los strikes
        return mapToResponseDto(reputationRepository.save(reputation));
    }

    /**
     * Lista todas las reputaciones de clientes para un negocio especifico.
     * Requiere validacion de ownership.
     */
    @Transactional(readOnly = true)
    public Page<ClientReputationResponseDto> getReputationsByBusiness(Long businessId, Long ownerId, Pageable pageable) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para ver la reputacion de clientes en este negocio.");
        }

        return reputationRepository.findByBusiness(business, pageable).map(this::mapToResponseDto);
    }

    /**
     * Busca el registro de reputacion o lo crea si no existe.
     */
    private ClientReputation getOrCreateReputation(User client, Business business) {
        return reputationRepository.findByClientAndBusiness(client, business)
                .orElseGet(() -> {
                    ClientReputation newReputation = new ClientReputation();
                    newReputation.setClient(client);
                    newReputation.setBusiness(business);
                    newReputation.setStrikeCount(0);
                    newReputation.setIsBlocked(false);
                    return reputationRepository.save(newReputation);
                });
    }

    /**
     * Valida que el usuario que hace la operacion sea el dueno del negocio.
     */
    private ClientReputation getReputationWithOwnerValidation(Long clientId, Long businessId, Long ownerId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + businessId));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para gestionar la reputacion de clientes en este negocio.");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clientId));

        return reputationRepository.findByClientAndBusiness(client, business)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontro el registro de reputacion para este cliente en este negocio."
                ));
    }

    /**
     * Mapea una entidad ClientReputation a su DTO de respuesta.
     */
    private ClientReputationResponseDto mapToResponseDto(ClientReputation reputation) {
        return ClientReputationResponseDto.builder()
                .id(reputation.getId())
                .clientId(reputation.getClient().getId())
                .clientEmail(reputation.getClient().getEmail())
                .strikeCount(reputation.getStrikeCount())
                .isBlocked(reputation.getIsBlocked())
                .lastUpdated(reputation.getLastUpdated())
                .build();
    }
}
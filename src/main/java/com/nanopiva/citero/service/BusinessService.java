package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.BusinessCreateRequestDto;
import com.nanopiva.citero.dto.business.BusinessResponseDto;
import com.nanopiva.citero.dto.business.BusinessConfigResponseDto;
import com.nanopiva.citero.dto.business.BusinessUpdateDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.BusinessSchedule;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientReputationRepository clientReputationRepository;

    public BusinessService(BusinessRepository businessRepository,
                           UserRepository userRepository,
                           StorageService storageService,
                           StaffRepository staffRepository,
                           AppointmentRepository appointmentRepository,
                           ClientReputationRepository clientReputationRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientReputationRepository = clientReputationRepository;
    }

    @Transactional
    public BusinessResponseDto createBusiness(Long ownerId, BusinessCreateRequestDto requestDto) {
        if (businessRepository.existsBySlug(requestDto.getSlug())) {
            throw new DuplicateResourceException("El slug '" + requestDto.getSlug() + "' ya está en uso. Elige otro.");
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario propietario no encontrado."));

        Business business = new Business();
        business.setName(requestDto.getName());
        business.setSlug(requestDto.getSlug());
        business.setDescription(requestDto.getDescription());
        business.setOwner(owner);
        business.setLogoUrl(requestDto.getLogoUrl());

        // Mapeo de campos de contacto y ubicación
        business.setAddress(requestDto.getAddress());
        business.setLatitude(requestDto.getLatitude());
        business.setLongitude(requestDto.getLongitude());
        business.setPhone(requestDto.getPhone());
        business.setCoverImageUrl(requestDto.getCoverImageUrl());
        business.setInstagramUrl(requestDto.getInstagramUrl());
        business.setFacebookUrl(requestDto.getFacebookUrl());
        business.setTiktokUrl(requestDto.getTiktokUrl());
        business.setTwitterUrl(requestDto.getTwitterUrl());
        business.setWhatsappNumber(requestDto.getWhatsappNumber());

        BusinessConfig defaultConfig = createDefaultConfig(business);
        business.setConfig(defaultConfig);

        // Sin horarios, el motor de disponibilidad no ofrece turnos.
        business.getSchedules().addAll(createDefaultSchedules(business, defaultConfig));

        Business savedBusiness = businessRepository.save(business);

        return mapToResponseDto(savedBusiness);
    }

    @Transactional(readOnly = true)
    public BusinessResponseDto getBusinessBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con slug: " + slug));
        return mapToResponseDto(business);
    }

    private BusinessConfig createDefaultConfig(Business business) {
        return BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC) // Por defecto: mínima fricción para empezar a recibir reservas
                .cancellationToleranceHours(24)
                .enablePenalties(true)
                .maxStrikes(3)
                .defaultOpeningTime(LocalTime.of(9, 0))  // 09:00
                .defaultClosingTime(LocalTime.of(18, 0)) // 18:00
                .build();
    }

    /**
     * Genera un horario por defecto por cada día de la semana, usando la apertura y
     * cierre por defecto de la configuración. Todos los días quedan abiertos; el dueño
     * puede cerrar los que no atienda desde la configuración.
     */
    private List<BusinessSchedule> createDefaultSchedules(Business business, BusinessConfig config) {
        return Arrays.stream(BusinessSchedule.DayOfWeek.values())
                .map(day -> BusinessSchedule.builder()
                        .business(business)
                        .dayOfWeek(day)
                        .openTime(config.getDefaultOpeningTime())
                        .closeTime(config.getDefaultClosingTime())
                        .isClosed(false)
                        .build())
                .toList();
    }

    private BusinessResponseDto mapToResponseDto(Business business) {
        return BusinessResponseDto.builder()
                .id(business.getId())
                .name(business.getName())
                .slug(business.getSlug())
                .description(business.getDescription())
                .createdAt(business.getCreatedAt())
                .config(mapConfigToDto(business.getConfig()))
                .logoUrl(business.getLogoUrl())
                .address(business.getAddress())
                .latitude(business.getLatitude())
                .longitude(business.getLongitude())
                .phone(business.getPhone())
                .coverImageUrl(business.getCoverImageUrl())
                .instagramUrl(business.getInstagramUrl())
                .facebookUrl(business.getFacebookUrl())
                .tiktokUrl(business.getTiktokUrl())
                .twitterUrl(business.getTwitterUrl())
                .whatsappNumber(business.getWhatsappNumber())
                .timezone(business.getTimezone())
                .build();
    }

    private BusinessConfigResponseDto mapConfigToDto(BusinessConfig config) {
        if (config == null) return null;
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

    public BusinessResponseDto getBusinessById(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + id));
        return mapToResponseDto(business);
    }

    public Business getEntityById(Long id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + id));
    }

    public BusinessResponseDto updateBusiness(Long id, Long ownerId, BusinessUpdateDto updateDto) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + id));

        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este negocio.");
        }

        // Campos existentes
        if (updateDto.getName() != null) {
            business.setName(updateDto.getName());
        }
        if (updateDto.getDescription() != null) {
            business.setDescription(updateDto.getDescription());
        }
        if(updateDto.getLogoUrl() != null) {
            business.setLogoUrl(updateDto.getLogoUrl());
        }

        // Campos opcionales (lógica PATCH)
        if (updateDto.getAddress() != null) {
            business.setAddress(updateDto.getAddress());
        }
        if (updateDto.getLatitude() != null) {
            business.setLatitude(updateDto.getLatitude());
        }
        if (updateDto.getLongitude() != null) {
            business.setLongitude(updateDto.getLongitude());
        }
        if (updateDto.getPhone() != null) {
            business.setPhone(updateDto.getPhone());
        }
        if (updateDto.getCoverImageUrl() != null) {
            business.setCoverImageUrl(updateDto.getCoverImageUrl());
        }
        if (updateDto.getInstagramUrl() != null) {
            business.setInstagramUrl(updateDto.getInstagramUrl());
        }
        if (updateDto.getFacebookUrl() != null) {
            business.setFacebookUrl(updateDto.getFacebookUrl());
        }
        if (updateDto.getTiktokUrl() != null) {
            business.setTiktokUrl(updateDto.getTiktokUrl());
        }
        if (updateDto.getTwitterUrl() != null) {
            business.setTwitterUrl(updateDto.getTwitterUrl());
        }
        if (updateDto.getWhatsappNumber() != null) {
            business.setWhatsappNumber(updateDto.getWhatsappNumber());
        }
        if (updateDto.getTimezone() != null) {
            business.setTimezone(resolveTimezone(updateDto.getTimezone()));
        }

        Business saved = businessRepository.save(business);
        return mapToResponseDto(saved);
    }

    private String resolveTimezone(String timezone) {
        if (timezone.isBlank()) {
            throw new BadRequestException("La zona horaria no puede estar vacía.");
        }
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw new BadRequestException("La zona horaria '" + timezone + "' no es válida.");
        }
    }

    @Transactional
    public BusinessResponseDto updateLogo(Long id, Long ownerId, MultipartFile file) {
        Business business = getOwnedBusiness(id, ownerId);
        String url = storageService.upload(file, "businesses/" + id, ImageType.LOGO);
        business.setLogoUrl(url);
        return mapToResponseDto(businessRepository.save(business));
    }

    @Transactional
    public BusinessResponseDto updateCover(Long id, Long ownerId, MultipartFile file) {
        Business business = getOwnedBusiness(id, ownerId);
        String url = storageService.upload(file, "businesses/" + id, ImageType.COVER);
        business.setCoverImageUrl(url);
        return mapToResponseDto(businessRepository.save(business));
    }

    /**
     * Elimina un negocio (solo el dueño). Borra primero los turnos y las
     * reputaciones asociadas para no violar las FKs, y luego el negocio, que
     * elimina en cascada su configuración, horarios, servicios y staff.
     */
    @Transactional
    public void deleteBusiness(Long id, Long ownerId) {
        Business business = getOwnedBusiness(id, ownerId);

        for (Staff staff : staffRepository.findByBusiness(business)) {
            appointmentRepository.deleteAll(appointmentRepository.findByStaff(staff));
        }

        clientReputationRepository.deleteByBusiness(business);

        businessRepository.delete(business);
    }

    private Business getOwnedBusiness(Long id, Long ownerId) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con ID: " + id));
        if (!business.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("No tienes permiso para modificar este negocio.");
        }
        return business;
    }

    public List<BusinessResponseDto> getBusinessesByOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + ownerId));
        return businessRepository.findByOwner(owner).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<BusinessResponseDto> searchBusinesses(String name, Pageable pageable) {
        Page<Business> businesses;
        if (name != null && !name.isBlank()) {
            businesses = businessRepository.findByNameContainingIgnoreCase(name, pageable);
        } else {
            businesses = businessRepository.findAll(pageable);
        }
        return businesses.map(this::mapToResponseDto);
    }
}
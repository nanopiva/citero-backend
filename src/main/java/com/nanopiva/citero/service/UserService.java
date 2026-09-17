package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.dto.user.UserUpdateDto;
import com.nanopiva.citero.dto.user.WorkspaceResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ClientReputationRepository;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.repository.RefreshTokenRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessRepository businessRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientReputationRepository clientReputationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpTokenRepository otpTokenRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       BusinessRepository businessRepository, StaffRepository staffRepository,
                       AppointmentRepository appointmentRepository,
                       ClientReputationRepository clientReputationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       OtpTokenRepository otpTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessRepository = businessRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientReputationRepository = clientReputationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpTokenRepository = otpTokenRepository;
    }

    @Transactional
    public UserResponseDto register(RegisterRequestDto requestDto) {
        Optional<User> existing = userRepository.findByEmail(requestDto.getEmail());

        User user;
        if (existing.isPresent()) {
            if (!Boolean.TRUE.equals(existing.get().getIsGuest())) {
                throw new DuplicateResourceException("El email " + requestDto.getEmail() + " ya está registrado.");
            }
            // Cuenta creada al reservar como invitado: la reclama quien se registra con ese
            // email, conservando sus reservas previas.
            user = existing.get();
            user.setIsGuest(false);
        } else {
            user = new User();
            user.setEmail(requestDto.getEmail());
        }

        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setPhone(requestDto.getPhone());
        User savedUser = userRepository.save(user);

        linkPendingInvitation(savedUser, requestDto.getInvitationToken());

        return mapToResponseDto(savedUser);
    }

    /**
     * Si el registro viene con un token de invitación válido, vincula el perfil de staff
     * pendiente y marca el email como verificado (la invitación prueba que es su correo).
     */
    private void linkPendingInvitation(User user, String invitationToken) {
        if (invitationToken == null || invitationToken.isBlank()) {
            return;
        }
        staffRepository.findByInvitationToken(invitationToken).ifPresent(staff -> {
            boolean valid = staff.getUser() == null
                    && staff.getContactEmail() != null
                    && staff.getContactEmail().equalsIgnoreCase(user.getEmail())
                    && staff.getInvitationExpiresAt() != null
                    && staff.getInvitationExpiresAt().isAfter(LocalDateTime.now());
            if (valid) {
                staff.setUser(user);
                staff.setContactEmail(null);
                staff.setInvitationToken(null);
                staff.setInvitationExpiresAt(null);
                staffRepository.save(staff);

                user.setEmailVerified(true);
                userRepository.save(user);
            }
        });
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return mapToResponseDto(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
        return mapToResponseDto(user);
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserUpdateDto updateDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // El email no se puede modificar: es el identificador de la cuenta y del login.

        if (updateDto.getPassword() != null && !updateDto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(updateDto.getPassword()));
        }

        if (updateDto.getPhone() != null) {
            user.setPhone(updateDto.getPhone());
        }

        User updatedUser = userRepository.save(user);
        return mapToResponseDto(updatedUser);
    }

    /**
     * Elimina la cuenta del usuario y todos sus datos asociados: turnos (como
     * cliente o profesional), reputaciones, negocios propios (con su config,
     * horarios, servicios y staff), membresías de staff, refresh tokens y OTPs.
     * Operación destructiva e irreversible.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = getUserEntityById(userId);

        List<Staff> memberships = staffRepository.findByUser(user);
        List<Business> ownedBusinesses = businessRepository.findByOwner(user);

        appointmentRepository.deleteAll(appointmentRepository.findByClient(user));

        for (Staff staff : memberships) {
            appointmentRepository.deleteAll(appointmentRepository.findByStaff(staff));
        }

        for (Business business : ownedBusinesses) {
            for (Staff staff : staffRepository.findByBusiness(business)) {
                appointmentRepository.deleteAll(appointmentRepository.findByStaff(staff));
            }
            clientReputationRepository.deleteByBusiness(business);
        }

        clientReputationRepository.deleteByClient(user);

        staffRepository.deleteAll(staffRepository.findByUser(user));

        businessRepository.deleteAll(ownedBusinesses);

        refreshTokenRepository.deleteByUser(user);
        otpTokenRepository.deleteByTarget(user.getEmail());

        userRepository.delete(user);
    }

    @Transactional
    public User findOrCreateGuestUser(String email, String phone) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User guest = new User();
                    guest.setEmail(email);
                    guest.setPhone(phone);
                    guest.setPassword(passwordEncoder.encode("guest_" + System.currentTimeMillis()));
                    guest.setIsGuest(true);
                    return userRepository.save(guest);
                });
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponseDto> getWorkspacesForUser(Long userId) {
        User user = getUserEntityById(userId);
        List<WorkspaceResponseDto> workspaces = new ArrayList<>();

        businessRepository.findByOwner(user).forEach(business -> {
            workspaces.add(WorkspaceResponseDto.builder()
                    .businessId(business.getId())
                    .businessName(business.getName())
                    .slug(business.getSlug())
                    .logoUrl(business.getLogoUrl())
                    .role("OWNER")
                    .build());
        });

        staffRepository.findByUser(user).forEach(staff -> {
            workspaces.add(WorkspaceResponseDto.builder()
                    .businessId(staff.getBusiness().getId())
                    .businessName(staff.getBusiness().getName())
                    .slug(staff.getBusiness().getSlug())
                    .logoUrl(staff.getBusiness().getLogoUrl())
                    .role("STAFF")
                    .build());
        });

        return workspaces;
    }

    private UserResponseDto mapToResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
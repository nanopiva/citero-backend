package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.user.RegisterRequestDto;
import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.dto.user.UserUpdateDto;
import com.nanopiva.citero.dto.user.WorkspaceResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.security.CommonPasswordCheck;
import com.nanopiva.citero.security.PwnedPasswordService;
import com.nanopiva.citero.util.Emails;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.BusinessClientRepository;
import com.nanopiva.citero.repository.OtpTokenRepository;
import com.nanopiva.citero.repository.RefreshTokenRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessRepository businessRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessClientRepository businessClientRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PwnedPasswordService pwnedPasswordService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       BusinessRepository businessRepository, StaffRepository staffRepository,
                       AppointmentRepository appointmentRepository,
                       BusinessClientRepository businessClientRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       OtpTokenRepository otpTokenRepository,
                       RefreshTokenService refreshTokenService,
                       PwnedPasswordService pwnedPasswordService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessRepository = businessRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
        this.businessClientRepository = businessClientRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpTokenRepository = otpTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.pwnedPasswordService = pwnedPasswordService;
    }

    @Transactional
    public UserResponseDto register(RegisterRequestDto requestDto) {
        String email = Emails.normalize(requestDto.getEmail());
        Optional<User> existing = userRepository.findByEmail(email);

        User user;
        if (existing.isPresent()) {
            if (!Boolean.TRUE.equals(existing.get().getIsGuest())) {
                // Mensaje genérico para no confirmar si el email ya está registrado.
                throw new DuplicateResourceException(
                        "No se pudo completar el registro. Si ya tenés cuenta, iniciá sesión o recuperá tu contraseña.");
            }
            // Cuenta creada al reservar como invitado: la reclama quien se registra con ese
            // email, conservando sus reservas previas.
            user = existing.get();
            user.setIsGuest(false);
        } else {
            user = new User();
            user.setEmail(email);
        }

        if (CommonPasswordCheck.isCommon(requestDto.getPassword())) {
            throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
        }

        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setPhone(requestDto.getPhone());
        user.setName(normalizeName(requestDto.getName()));
        // La cuenta sólo llega acá tras verificar el OTP de email (ver AuthService.register).
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);

        linkPendingStaff(savedUser);

        return mapToResponseDto(savedUser);
    }

    /**
     * Vincula al usuario con todos los perfiles de staff pendientes que fueron invitados
     * a su email. Se invoca al registrarse y también en cada login, para que una persona
     * invitada que se registra por su cuenta (sin abrir el link de la invitación) quede
     * vinculada igual. La invitación al email prueba que el correo es suyo, por eso la
     * cuenta también queda marcada como verificada.
     */
    @Transactional
    public void linkPendingStaff(User user) {
        // Sólo vincula si el email está verificado (evita reclamar un perfil de staff ajeno).
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        List<Staff> pending = staffRepository.findByContactEmailIgnoreCase(user.getEmail());
        for (Staff staff : pending) {
            if (staff.getUser() != null) {
                continue;
            }
            staff.setUser(user);
            staff.setContactEmail(null);
            staffRepository.save(staff);

            user.setEmailVerified(true);
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return mapToResponseDto(user);
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserUpdateDto updateDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // El email no se puede modificar: es el identificador de la cuenta y del login.

        String newPassword = updateDto.getPassword();
        boolean passwordChanged = newPassword != null && !newPassword.isBlank();
        if (passwordChanged) {
            // Reautenticación: no se cambia la contraseña sin probar la actual.
            String currentPassword = updateDto.getCurrentPassword();
            if (currentPassword == null || currentPassword.isBlank()
                    || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new BadRequestException("La contraseña actual es incorrecta.");
            }
            if (newPassword.length() < 8 || newPassword.length() > 72) {
                throw new BadRequestException("La contraseña debe tener entre 8 y 72 caracteres");
            }
            if (CommonPasswordCheck.isCommon(newPassword)) {
                throw new BadRequestException("Esa contraseña es demasiado común. Elegí otra.");
            }
            if (pwnedPasswordService.isBreached(newPassword)) {
                throw new BadRequestException(
                        "Esa contraseña apareció en una filtración de datos conocida. Elegí otra.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (updateDto.getPhone() != null) {
            String phone = updateDto.getPhone().trim();
            // Un string vacío borra el teléfono; null significa "no tocar".
            user.setPhone(phone.isEmpty() ? null : phone);
        }

        if (updateDto.getName() != null) {
            // Un string vacío borra el nombre; null significa "no tocar".
            user.setName(normalizeName(updateDto.getName()));
        }

        User updatedUser = userRepository.save(user);

        if (passwordChanged) {
            // Cierra las sesiones existentes tras un cambio de contraseña.
            refreshTokenService.revokeAllForUser(updatedUser);
        }

        return mapToResponseDto(updatedUser);
    }

    /**
     * Elimina la cuenta del usuario y todos sus datos asociados: turnos (como
     * cliente o profesional), bloqueos de clientes, negocios propios (con su config,
     * horarios, servicios y staff), membresías de staff, refresh tokens y OTPs.
     * Operación destructiva e irreversible.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = getUserEntityById(userId);

        // Reautenticación: la baja de cuenta es destructiva e irreversible.
        if (password == null || password.isBlank()
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("La contraseña es incorrecta.");
        }

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
            businessClientRepository.deleteByBusiness(business);
        }

        businessClientRepository.deleteByClient(user);

        staffRepository.deleteAll(staffRepository.findByUser(user));

        businessRepository.deleteAll(ownedBusinesses);

        refreshTokenRepository.deleteByUser(user);
        otpTokenRepository.deleteByTarget(user.getEmail());

        userRepository.delete(user);
    }

    @Transactional
    public User findOrCreateGuestUser(String email, String phone, String name) {
        String normalizedEmail = Emails.normalize(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> {
                    User guest = new User();
                    guest.setEmail(normalizedEmail);
                    guest.setPhone(phone);
                    guest.setName(normalizeName(name));
                    // Contraseña aleatoria e inutilizable: la cuenta invitado no puede
                    // iniciar sesión (emailVerified=false) y se reclama al registrarse.
                    guest.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    guest.setIsGuest(true);
                    return userRepository.save(guest);
                });
    }

    /** trim; vacío se guarda como null (semántica compartida por registro, perfil e invitado). */
    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                    .timezone(business.getTimezone())
                    .role("OWNER")
                    .build());
        });

        staffRepository.findByUser(user).forEach(staff -> {
            workspaces.add(WorkspaceResponseDto.builder()
                    .businessId(staff.getBusiness().getId())
                    .businessName(staff.getBusiness().getName())
                    .slug(staff.getBusiness().getSlug())
                    .logoUrl(staff.getBusiness().getLogoUrl())
                    .timezone(staff.getBusiness().getTimezone())
                    .role("STAFF")
                    .staffId(staff.getId())
                    .build());
        });

        return workspaces;
    }

    private UserResponseDto mapToResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .name(user.getName())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
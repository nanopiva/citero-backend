package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.StaffCreateRequestDto;
import com.nanopiva.citero.dto.business.StaffResponseDto;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.DuplicateResourceException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Transactional
class StaffServiceTest extends IntegrationTest {

    @Autowired private StaffService staffService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;

    @MockitoBean private StaffNotificationService staffNotificationService;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("staff-" + tag + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .build());
    }

    private String uniqueSlug(String base) {
        return base + "-" + System.nanoTime();
    }

    private Business seedBusiness(User owner, String slug) {
        Business business = Business.builder().owner(owner).name("Negocio " + slug).slug(slug).build();
        BusinessConfig config = BusinessConfig.builder()
                .business(business)
                .reservationMode(BusinessConfig.ReservationMode.PUBLIC)
                .cancellationToleranceHours(24)
                .defaultOpeningTime(LocalTime.of(9, 0))
                .defaultClosingTime(LocalTime.of(18, 0))
                .enablePenalties(true)
                .maxStrikes(3)
                .enableReminders(true)
                .reminder24hEnabled(true)
                .reminder2hEnabled(true)
                .build();
        business.setConfig(config);
        return businessRepository.save(business);
    }

    @Test
    void createStaffWithUnknownEmailCreatesOrphanProfile() {
        User owner = persistUser("orphan-owner");
        Business business = seedBusiness(owner, uniqueSlug("orphan"));
        String email = "orphan-" + System.nanoTime() + "@test.com";

        StaffResponseDto response = staffService.createStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(email).customName("Ana").build());

        assertFalse(response.isHasClaimedAccount(), "Un email sin cuenta genera un perfil huérfano");
        assertEquals(email, response.getUserEmail(), "El email de contacto debe exponerse como userEmail");
        verify(staffNotificationService).sendStaffInvitation(eq(email), eq("Ana"), eq(business.getName()), eq(false), anyString());
    }

    @Test
    void createStaffWithRegisteredEmailLinksUser() {
        User owner = persistUser("reg-owner");
        User member = persistUser("reg-member");
        Business business = seedBusiness(owner, uniqueSlug("reg"));

        StaffResponseDto response = staffService.createStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(member.getEmail()).customName("Beto").build());

        assertTrue(response.isHasClaimedAccount(), "Un email registrado se vincula a la cuenta");
        assertEquals(member.getEmail(), response.getUserEmail());
        verify(staffNotificationService).sendStaffInvitation(eq(member.getEmail()), eq("Beto"), eq(business.getName()), eq(true), any());
    }

    @Test
    void ownerAddingHimselfDoesNotSendInvitation() {
        User owner = persistUser("self-owner");
        Business business = seedBusiness(owner, uniqueSlug("self"));

        StaffResponseDto response = staffService.createStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(owner.getEmail()).customName("Dueño").build());

        assertTrue(response.isHasClaimedAccount(), "El dueño ya tiene cuenta y queda vinculado");
        verify(staffNotificationService, never()).sendStaffInvitation(anyString(), any(), anyString(), anyBoolean(), any());
    }

    @Test
    void createStaffRejectsDuplicates() {
        User owner = persistUser("dup-owner");
        Business business = seedBusiness(owner, uniqueSlug("dup"));
        String email = "dup-" + System.nanoTime() + "@test.com";

        staffService.createStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(email).build());

        assertThrows(DuplicateResourceException.class, () -> staffService.createStaff(
                business.getId(), owner.getId(), StaffCreateRequestDto.builder().email(email).build()),
                "No se puede repetir un perfil con el mismo correo en el negocio");
    }

    @Test
    void createStaffByNonOwnerIsRejected() {
        User owner = persistUser("perm-owner");
        User intruder = persistUser("perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("perm"));

        assertThrows(ForbiddenException.class, () -> staffService.createStaff(business.getId(), intruder.getId(),
                StaffCreateRequestDto.builder().email("x-" + System.nanoTime() + "@test.com").build()),
                "Solo el dueño puede agregar empleados");
    }

    @Test
    void getStaffByBusinessIdListsTeam() {
        User owner = persistUser("list-owner");
        Business business = seedBusiness(owner, uniqueSlug("list"));
        staffService.createStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email("list-" + System.nanoTime() + "@test.com").build());

        List<StaffResponseDto> staff = staffService.getStaffByBusinessId(business.getId());

        assertEquals(1, staff.size(), "Debe listarse el empleado creado");
    }

    @Test
    void updateStaffChangesNameAndServices() {
        User owner = persistUser("upd-owner");
        Business business = seedBusiness(owner, uniqueSlug("upd"));
        com.nanopiva.citero.entity.Service service = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(business)
                        .name("Corte")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        StaffResponseDto response = staffService.updateStaff(staff.getId(), owner.getId(),
                StaffCreateRequestDto.builder().customName("Ana María").serviceIds(Set.of(service.getId())).build());

        assertEquals("Ana María", response.getCustomName(), "El nombre debe actualizarse");
        assertEquals(1, response.getServices().size(), "El servicio debe quedar asignado");
    }

    @Test
    void updateStaffByNonOwnerIsRejected() {
        User owner = persistUser("upd-perm-owner");
        User intruder = persistUser("upd-perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("upd-perm"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        assertThrows(ForbiddenException.class, () -> staffService.updateStaff(staff.getId(), intruder.getId(),
                StaffCreateRequestDto.builder().customName("Hackeado").build()),
                "Solo el dueño puede modificar empleados");
    }

    @Test
    void deleteStaffRemovesProfile() {
        User owner = persistUser("del-owner");
        Business business = seedBusiness(owner, uniqueSlug("del"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        staffService.deleteStaff(staff.getId(), owner.getId());

        assertTrue(staffRepository.findById(staff.getId()).isEmpty(), "El empleado debe eliminarse");
    }

    @Test
    void deleteStaffByNonOwnerIsRejected() {
        User owner = persistUser("del-perm-owner");
        User intruder = persistUser("del-perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("del-perm"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        assertThrows(ForbiddenException.class, () -> staffService.deleteStaff(staff.getId(), intruder.getId()),
                "Solo el dueño puede eliminar empleados");
    }

    @Test
    void leaveStaffRemovesOwnProfile() {
        User owner = persistUser("leave-owner");
        User member = persistUser("leave-member");
        Business business = seedBusiness(owner, uniqueSlug("leave"));
        Staff staff = staffRepository.save(Staff.builder().business(business).user(member).build());

        staffService.leaveStaff(business.getId(), member.getId());

        assertTrue(staffRepository.findById(staff.getId()).isEmpty(), "El empleado debe poder salir del equipo");
    }

    @Test
    void leaveStaffFailsWhenNotAMember() {
        User owner = persistUser("leave2-owner");
        User stranger = persistUser("leave2-stranger");
        Business business = seedBusiness(owner, uniqueSlug("leave2"));

        assertThrows(ResourceNotFoundException.class, () -> staffService.leaveStaff(business.getId(), stranger.getId()),
                "Un usuario ajeno al equipo no puede salir de él");
    }

    @Test
    void addOwnerAsStaffLinksOwner() {
        User owner = persistUser("addself-owner");
        Business business = seedBusiness(owner, uniqueSlug("addself"));

        StaffResponseDto response = staffService.addOwnerAsStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(owner.getEmail()).customName("Dueño").build());

        assertTrue(response.isHasClaimedAccount(), "El dueño queda vinculado como profesional");
    }

    @Test
    void addOwnerAsStaffRejectsDuplicate() {
        User owner = persistUser("addself2-owner");
        Business business = seedBusiness(owner, uniqueSlug("addself2"));
        staffService.addOwnerAsStaff(business.getId(), owner.getId(),
                StaffCreateRequestDto.builder().email(owner.getEmail()).build());

        assertThrows(DuplicateResourceException.class, () -> staffService.addOwnerAsStaff(
                business.getId(), owner.getId(), StaffCreateRequestDto.builder().email(owner.getEmail()).build()),
                "No se puede agregar dos veces al dueño");
    }

    @Test
    void assignServicesFromAnotherBusinessIsRejected() {
        User ownerA = persistUser("assign-a");
        User ownerB = persistUser("assign-b");
        Business businessA = seedBusiness(ownerA, uniqueSlug("assign-a"));
        Business businessB = seedBusiness(ownerB, uniqueSlug("assign-b"));
        Staff staff = staffRepository.save(Staff.builder().business(businessA).customName("Ana").build());
        com.nanopiva.citero.entity.Service foreignService = serviceRepository.save(
                com.nanopiva.citero.entity.Service.builder()
                        .business(businessB)
                        .name("Ajeno")
                        .durationMinutes(30)
                        .price(BigDecimal.TEN)
                        .build());

        assertThrows(BadRequestException.class, () -> staffService.assignServicesToStaff(
                staff.getId(), ownerA.getId(), Set.of(foreignService.getId())),
                "No se pueden asignar servicios de otro negocio");
    }

    @Test
    void assignUnknownServiceIdsAreSilentlyIgnored() {
        User owner = persistUser("assign-unknown");
        Business business = seedBusiness(owner, uniqueSlug("assign-unknown"));
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        StaffResponseDto response = staffService.assignServicesToStaff(
                staff.getId(), owner.getId(), Set.of(999_999L));

        assertTrue(response.getServices().isEmpty(),
                "Los IDs de servicio inexistentes se ignoran");
    }
}

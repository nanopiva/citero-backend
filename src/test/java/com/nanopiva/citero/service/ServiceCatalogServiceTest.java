package com.nanopiva.citero.service;

import com.nanopiva.citero.dto.business.ServiceCreateRequestDto;
import com.nanopiva.citero.dto.business.ServiceResponseDto;
import com.nanopiva.citero.dto.business.ServiceUpdateDto;
import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessConfig;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.exception.ConflictException;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.exception.ResourceNotFoundException;
import com.nanopiva.citero.repository.AppointmentRepository;
import com.nanopiva.citero.repository.BusinessRepository;
import com.nanopiva.citero.repository.ServiceRepository;
import com.nanopiva.citero.repository.StaffRepository;
import com.nanopiva.citero.repository.UserRepository;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ServiceCatalogServiceTest extends IntegrationTest {

    @Autowired private ServiceCatalogService serviceCatalogService;
    @Autowired private UserRepository userRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private User persistUser(String tag) {
        return userRepository.save(User.builder()
                .email("svc-" + tag + "-" + System.nanoTime() + "@test.com")
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

    private com.nanopiva.citero.entity.Service seedService(Business business, String name) {
        return serviceRepository.save(com.nanopiva.citero.entity.Service.builder()
                .business(business)
                .name(name)
                .durationMinutes(30)
                .price(BigDecimal.TEN)
                .build());
    }

    @Test
    void createServicePersistsAndMapsFields() {
        User owner = persistUser("create");
        Business business = seedBusiness(owner, uniqueSlug("create"));

        ServiceResponseDto response = serviceCatalogService.createService(business.getId(), owner.getId(),
                ServiceCreateRequestDto.builder().name("Corte").durationMinutes(45).price(new BigDecimal("15.50")).build());

        assertEquals("Corte", response.getName());
        assertEquals(45, response.getDurationMinutes());
        assertEquals(0, response.getPrice().compareTo(new BigDecimal("15.50")));
    }

    @Test
    void createServiceByNonOwnerIsRejected() {
        User owner = persistUser("create-perm-owner");
        User intruder = persistUser("create-perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("create-perm"));

        assertThrows(ForbiddenException.class, () -> serviceCatalogService.createService(
                business.getId(), intruder.getId(),
                ServiceCreateRequestDto.builder().name("Corte").durationMinutes(30).price(BigDecimal.TEN).build()),
                "Solo el dueño puede crear servicios");
    }

    @Test
    void getServicesByBusinessIdListsCatalog() {
        User owner = persistUser("list");
        Business business = seedBusiness(owner, uniqueSlug("list"));
        seedService(business, "Corte");
        seedService(business, "Color");

        List<ServiceResponseDto> services = serviceCatalogService.getServicesByBusinessId(business.getId());

        assertEquals(2, services.size(), "Deben listarse los servicios del negocio");
    }

    @Test
    void getServicesForUnknownBusinessFails() {
        assertThrows(ResourceNotFoundException.class, () -> serviceCatalogService.getServicesByBusinessId(999_999L),
                "Un negocio inexistente debe producir ResourceNotFoundException");
    }

    @Test
    void updateServiceChangesFields() {
        User owner = persistUser("update");
        Business business = seedBusiness(owner, uniqueSlug("update"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        ServiceResponseDto response = serviceCatalogService.updateService(service.getId(), owner.getId(),
                ServiceUpdateDto.builder().name("Corte VIP").durationMinutes(60).price(new BigDecimal("25")).build());

        assertEquals("Corte VIP", response.getName());
        assertEquals(60, response.getDurationMinutes());
        assertEquals(0, response.getPrice().compareTo(new BigDecimal("25")));
    }

    @Test
    void updateServiceByNonOwnerIsRejected() {
        User owner = persistUser("update-perm-owner");
        User intruder = persistUser("update-perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("update-perm"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        assertThrows(ForbiddenException.class, () -> serviceCatalogService.updateService(service.getId(), intruder.getId(),
                ServiceUpdateDto.builder().name("Hackeado").build()),
                "Solo el dueño puede modificar servicios");
    }

    @Test
    void deleteServiceRemovesIt() {
        User owner = persistUser("delete");
        Business business = seedBusiness(owner, uniqueSlug("delete"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        serviceCatalogService.deleteService(service.getId(), owner.getId());

        assertTrue(serviceRepository.findById(service.getId()).isEmpty(), "El servicio debe eliminarse");
    }

    @Test
    void deleteServiceByNonOwnerIsRejected() {
        User owner = persistUser("delete-perm-owner");
        User intruder = persistUser("delete-perm-intruder");
        Business business = seedBusiness(owner, uniqueSlug("delete-perm"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");

        assertThrows(ForbiddenException.class, () -> serviceCatalogService.deleteService(service.getId(), intruder.getId()),
                "Solo el dueño puede eliminar servicios");
    }

    @Test
    void deletingServiceWithAppointmentsIsRejected() {
        User owner = persistUser("fk-owner");
        User client = persistUser("fk-client");
        Business business = seedBusiness(owner, uniqueSlug("fk"));
        com.nanopiva.citero.entity.Service service = seedService(business, "Corte");
        Staff staff = staffRepository.save(Staff.builder().business(business).customName("Ana").build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        appointmentRepository.save(Appointment.builder()
                .client(client)
                .staff(staff)
                .service(service)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build());

        assertThrows(ConflictException.class,
                () -> serviceCatalogService.deleteService(service.getId(), owner.getId()),
                "Borrar un servicio con turnos asociados debe rechazarse con 409");
        assertTrue(serviceRepository.findById(service.getId()).isPresent(),
                "El servicio no debe eliminarse");
    }
}

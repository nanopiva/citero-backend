package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Appointment;
import com.nanopiva.citero.entity.Service;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByClient(User client);

    @EntityGraph(attributePaths = {"client", "staff", "staff.user", "service", "service.business"})
    Page<Appointment> findByClient(User client, Pageable pageable);

    List<Appointment> findByStaff(Staff staff);

    /**
     * Búsqueda paginada de turnos de un negocio con filtros opcionales.
     *
     * <p>Los filtros se pasan siempre con valor (los flags {@code filterBy*} indican si se
     * aplican) y el rango de fechas nunca es nulo: PostgreSQL no puede inferir el tipo de
     * un parámetro nulo usado en {@code ? is null}.</p>
     */
    @EntityGraph(attributePaths = {"client", "staff", "staff.user", "service", "service.business"})
    @Query("""
            select a from Appointment a
            where a.staff.business.id = :businessId
              and a.startTime >= :start
              and a.startTime < :end
              and (:filterByStaff = false or a.staff.id = :staffId)
              and (:filterByStatus = false or a.status = :status)
            """)
    Page<Appointment> searchByBusiness(@Param("businessId") Long businessId,
                                       @Param("filterByStaff") boolean filterByStaff,
                                       @Param("staffId") Long staffId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("filterByStatus") boolean filterByStatus,
                                       @Param("status") Appointment.AppointmentStatus status,
                                       Pageable pageable);
    boolean existsByService(Service service);
    List<Appointment> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Appointment> findByStaffAndStartTimeBetween(Staff staff, LocalDateTime start, LocalDateTime end);

    // Turnos de un negocio en un rango, con el servicio precargado para evitar N+1.
    @EntityGraph(attributePaths = {"service"})
    List<Appointment> findByService_Business_IdAndStartTimeBetween(Long businessId, LocalDateTime start, LocalDateTime end);

    // Turnos confirmados de varios profesionales en un rango (una sola query para el cálculo de disponibilidad).
    List<Appointment> findByStaffInAndStartTimeBetweenAndStatus(
            Collection<Staff> staff, LocalDateTime start, LocalDateTime end, Appointment.AppointmentStatus status);

    // Se cargan las asociaciones necesarias para enviar recordatorios sin mantener
    // abierta una transacción durante las llamadas de red al proveedor de email.
    @EntityGraph(attributePaths = {"client", "staff", "staff.user", "staff.business", "service"})
    List<Appointment> findByStatusAndStartTimeBetween(Appointment.AppointmentStatus status, LocalDateTime start, LocalDateTime end);

    /**
     * Bloquea (SELECT ... FOR UPDATE) el turno para serializar cambios de estado
     * concurrentes sobre el mismo turno (p. ej. evitar aplicar dos veces un strike
     * por NO_SHOW ante dobles clics). SQL nativo portable H2/PostgreSQL.
     * Debe invocarse dentro de una transacción de escritura.
     */
    @Query(value = "SELECT * FROM appointments WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    // "Claim" atómico del recordatorio: solo una instancia/ejecución marca la fila.
    @Modifying
    @Transactional
    @Query("update Appointment a set a.reminder24hSentAt = :now where a.id = :id and a.reminder24hSentAt is null")
    int claimReminder24h(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("update Appointment a set a.reminder2hSentAt = :now where a.id = :id and a.reminder2hSentAt is null")
    int claimReminder2h(@Param("id") Long id, @Param("now") LocalDateTime now);

    // Libera el claim si el envío falló, para que se reintente en la próxima ejecución.
    @Modifying
    @Transactional
    @Query("update Appointment a set a.reminder24hSentAt = null where a.id = :id")
    int releaseReminder24h(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update Appointment a set a.reminder2hSentAt = null where a.id = :id")
    int releaseReminder2h(@Param("id") Long id);
}
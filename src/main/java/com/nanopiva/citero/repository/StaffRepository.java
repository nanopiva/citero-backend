package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Staff;
import com.nanopiva.citero.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findByBusiness(Business business);

    // Devuelve todos los perfiles de empleado que tiene un usuario (para el Navbar)
    List<Staff> findByUser(User user);

    // Devuelve el perfil de empleado específico de un usuario en un local puntual
    Optional<Staff> findByUserAndBusiness(User user, Business business);

    // Valida que no se duplique el mismo empleado en el mismo local
    boolean existsByUserAndBusiness(User user, Business business);

    boolean existsByContactEmailAndBusiness(String contactEmail, Business business);
    List<Staff> findByContactEmailIgnoreCase(String contactEmail);

    /**
     * Bloquea (SELECT ... FOR UPDATE) el perfil del empleado para serializar reservas
     * concurrentes sobre el mismo profesional y evitar la doble reserva.
     * Se usa SQL nativo con {@code FOR UPDATE} (en lugar del lock pesimista de JPA)
     * para que sea portable entre H2 (desarrollo) y PostgreSQL (producción), ya que
     * Hibernate con dialecto PostgreSQL emite {@code FOR NO KEY UPDATE}, no soportado por H2.
     * Debe invocarse dentro de una transacción de escritura.
     */
    @Query(value = "SELECT * FROM staff WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Staff> findByIdForUpdate(@Param("id") Long id);

    /**
     * Bloquea todos los profesionales de un negocio, ordenados por id (orden de adquisición
     * consistente para evitar deadlocks). Se usa para la auto-asignación ("Cualquier
     * profesional"): se elige un profesional libre sobre el conjunto ya bloqueado.
     * Debe invocarse dentro de una transacción de escritura.
     */
    @Query(value = "SELECT * FROM staff WHERE business_id = :businessId ORDER BY id FOR UPDATE", nativeQuery = true)
    List<Staff> findByBusinessForUpdate(@Param("businessId") Long businessId);
}
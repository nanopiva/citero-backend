package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.ClientReputation;
import com.nanopiva.citero.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientReputationRepository extends JpaRepository<ClientReputation, Long> {
    Optional<ClientReputation> findByClientAndBusiness(User client, Business business);
    long countByBusinessAndIsBlockedTrue(Business business);

    /**
     * Bloquea (SELECT ... FOR UPDATE) la fila de reputación para serializar el
     * incremento de strikes y evitar el lost update. SQL nativo portable H2/PostgreSQL.
     * Debe invocarse dentro de una transacción de escritura.
     */
    @Query(value = "SELECT * FROM client_reputations WHERE client_id = :clientId AND business_id = :businessId FOR UPDATE",
            nativeQuery = true)
    Optional<ClientReputation> findByClientAndBusinessForUpdate(@Param("clientId") Long clientId,
                                                                @Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"client"})
    Page<ClientReputation> findByBusiness(Business business, Pageable pageable);

    // Borrado en cascada manual al eliminar un negocio o una cuenta de usuario.
    long deleteByBusiness(Business business);
    long deleteByClient(User client);
}
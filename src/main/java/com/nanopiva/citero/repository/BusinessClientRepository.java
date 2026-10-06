package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessClient;
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
public interface BusinessClientRepository extends JpaRepository<BusinessClient, Long> {
    Optional<BusinessClient> findByClientAndBusiness(User client, Business business);
    long countByBusinessAndIsBlockedTrue(Business business);

    /**
     * Bloquea (SELECT ... FOR UPDATE) la fila del cliente para serializar
     * modificaciones concurrentes y evitar el lost update. SQL nativo portable
     * H2/PostgreSQL. Debe invocarse dentro de una transacción de escritura.
     */
    @Query(value = "SELECT * FROM business_clients WHERE client_id = :clientId AND business_id = :businessId FOR UPDATE",
            nativeQuery = true)
    Optional<BusinessClient> findByClientAndBusinessForUpdate(@Param("clientId") Long clientId,
                                                              @Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"client"})
    Page<BusinessClient> findByBusiness(Business business, Pageable pageable);

    // Borrado en cascada manual al eliminar un negocio o una cuenta de usuario.
    long deleteByBusiness(Business business);
    long deleteByClient(User client);
}

package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.RefreshToken;
import com.nanopiva.citero.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Elimina todas las sesiones (refresh tokens) de un usuario. Se usa al
     * eliminar la cuenta para no dejar credenciales huérfanas.
     */
    long deleteByUser(User user);

    /**
     * Revoca todos los tokens activos de una familia de sesión.
     */
    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.sessionId = :sessionId and r.revoked = false")
    int revokeBySessionId(@Param("sessionId") String sessionId);

    /**
     * Elimina los tokens ya expirados (housekeeping).
     */
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.RefreshToken;
import com.nanopiva.citero.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * Igual que {@link #findByTokenHash} con lock pesimista de escritura: serializa
     * rotaciones concurrentes del mismo token para detectar el reuso.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

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
     * Revoca todos los refresh tokens activos de un usuario. Se usa al cambiar o resetear
     * la contraseña: invalida las sesiones emitidas antes del cambio (p. ej. un token robado).
     */
    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user and r.revoked = false")
    int revokeByUser(@Param("user") User user);

    /**
     * Elimina los tokens ya expirados (housekeeping).
     */
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

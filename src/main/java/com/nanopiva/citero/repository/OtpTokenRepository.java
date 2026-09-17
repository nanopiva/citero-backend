package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    // Filtra también por purpose para evitar que un OTP de reserva
    // se use para resetear contraseña (y viceversa).
    Optional<OtpToken> findFirstByTargetAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(
            String target, String purpose);

    @Modifying
    @Query("update OtpToken t set t.isUsed = true "
            + "where t.target = :target and t.purpose = :purpose and t.isUsed = false")
    int markActiveAsUsed(@Param("target") String target, @Param("purpose") String purpose);

    void deleteByExpirationTimeBefore(LocalDateTime dateTime);

    // Limpieza al eliminar la cuenta de un usuario (target = email o teléfono).
    long deleteByTarget(String target);
}
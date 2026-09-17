package com.nanopiva.citero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "business_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_mode", nullable = false, length = 20)
    private ReservationMode reservationMode;

    @Column(name = "cancellation_tolerance_hours", nullable = false)
    private Integer cancellationToleranceHours;

    @Column(name = "enable_penalties", nullable = false)
    @Builder.Default
    private Boolean enablePenalties = true;

    @Column(name = "max_strikes", nullable = false)
    @Builder.Default
    private Integer maxStrikes = 3;

    @Column(name = "default_opening_time", nullable = false)
    private LocalTime defaultOpeningTime;

    @Column(name = "default_closing_time", nullable = false)
    private LocalTime defaultClosingTime;

    // Configuración de recordatorios automáticos
    @Column(name = "enable_reminders", nullable = false)
    @Builder.Default
    private Boolean enableReminders = true;

    @Column(name = "reminder_24h_enabled", nullable = false)
    @Builder.Default
    private Boolean reminder24hEnabled = true;

    @Column(name = "reminder_2h_enabled", nullable = false)
    @Builder.Default
    private Boolean reminder2hEnabled = true;

    public enum ReservationMode {
        PUBLIC, AUTHENTICATED
    }
}
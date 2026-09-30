package com.nanopiva.citero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Una franja horaria (apertura/cierre) de un {@link BusinessScheduleDay}.
 * Un día puede tener varias franjas (p. ej. 07:00-12:00 y 17:00-20:00).
 */
@Entity
@Table(name = "business_schedule_periods")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSchedulePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BusinessScheduleDay scheduleDay;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;
}

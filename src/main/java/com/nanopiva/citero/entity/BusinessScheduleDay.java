package com.nanopiva.citero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Un "día" de la agenda de un negocio. Puede ser una <b>regla semanal</b>
 * ({@code dayOfWeek} seteado, se repite todas las semanas) o una
 * <b>excepción puntual</b> ({@code specificDate} seteado, aplica sólo a esa fecha).
 * Las franjas horarias del día viven en {@link BusinessSchedulePeriod}.
 */
@Entity
@Table(name = "business_schedule_days", uniqueConstraints = {
        @UniqueConstraint(name = "uk_schedule_day_weekday", columnNames = {"business_id", "day_of_week"}),
        @UniqueConstraint(name = "uk_schedule_day_date", columnNames = {"business_id", "specific_date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessScheduleDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = false;

    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("openTime ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BusinessSchedulePeriod> periods = new ArrayList<>();

    /** Indica si este día es una excepción puntual (en lugar de una regla semanal). */
    public boolean isException() {
        return specificDate != null;
    }

    public enum DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    }
}

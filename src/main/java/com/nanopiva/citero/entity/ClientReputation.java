package com.nanopiva.citero.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "client_reputations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "business_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Business business;

    @Column(name = "strike_count", nullable = false)
    @Builder.Default
    private Integer strikeCount = 0;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
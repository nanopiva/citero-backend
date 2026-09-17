package com.nanopiva.citero.entity;

import com.nanopiva.citero.util.BusinessTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "businesses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, nullable = false, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 255)
    private String address;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 255)
    private String instagramUrl;

    @Column(length = 255)
    private String facebookUrl;

    @Column(length = 255)
    private String tiktokUrl;

    @Column(length = 255)
    private String twitterUrl;

    @Column(length = 30)
    private String whatsappNumber;

    @Column(length = 60)
    private String timezone;

    // Relación 1:1 con BusinessConfig
    @OneToOne(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BusinessConfig config;

    // Relación 1:N con BusinessSchedule
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<BusinessSchedule> schedules = new HashSet<>();

    // Relación 1:N con Service
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Service> services = new HashSet<>();

    // Relación 1:N con Staff
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Staff> staffMembers = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timezone == null || timezone.isBlank()) {
            timezone = BusinessTime.DEFAULT_ZONE_ID;
        }
    }
}
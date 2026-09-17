package com.nanopiva.citero.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String target; // Email o teléfono

    @Column(nullable = false, length = 10)
    @ToString.Exclude
    private String code;

    @Column(nullable = false)
    private LocalDateTime expirationTime;

    @Column(nullable = false)
    private Boolean isUsed;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String purpose = "GUEST_VERIFICATION";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isUsed == null) this.isUsed = false;
        if (this.attempts == null) this.attempts = 0;
        if (this.purpose == null) this.purpose = "GUEST_VERIFICATION";
    }
}
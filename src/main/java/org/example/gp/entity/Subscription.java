package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Абонамент на счетоводна кантора (officeId).
 * Всяка кантора има точно един активен запис в тази таблица.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long officeId;

    /** BASIC или PRO */
    @Enumerated(EnumType.STRING)
    private PlanType plan;

    /** TRIAL, ACTIVE, PAST_DUE, CANCELED */
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private LocalDateTime trialEndsAt;

    private LocalDateTime currentPeriodEnd;

    /** Stripe Customer ID (cus_xxx) */
    private String stripeCustomerId;

    /** Stripe Subscription ID (sub_xxx) */
    private String stripeSubscriptionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

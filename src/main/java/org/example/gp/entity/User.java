package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Потребител = Счетоводна кантора (или служител на кантора).
 *
 * ROLE_ADMIN  → системен администратор, вижда всички кантори (само за поддръжка)
 * ROLE_OFFICE → собственик/admin на кантора, вижда САМО своите фирми
 * ROLE_USER   → служител на кантора, вижда САМО фирмите на своята кантора
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    /** Всички потребители на една кантора споделят един и същи officeId */
    private Long officeId;

    /** Показно ime на кантората (само за ROLE_OFFICE потребителя) */
    private String officeName;

    private String role;
}

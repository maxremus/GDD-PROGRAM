package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Одит лог — записва всяко значимо действие в системата
 * (кой, кога, какво е направил, от къде, и дали е успешно).
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    /** Потребителско ime на извършителя ("anonymous" ако няма логнат потребител) */
    private String username;

    private Long officeId;

    /** ROLE_ADMIN / ROLE_OFFICE / ROLE_USER */
    private String role;

    /** Кратко описание на действието, напр. "companies.add" */
    private String action;

    private String httpMethod;

    @Column(length = 500)
    private String requestUri;

    /** Параметри на заявката (без пароли), напр. "name=Test ЕООД, year=2026" */
    @Column(length = 2000)
    private String details;

    private String ipAddress;

    private boolean success;

    @Column(length = 2000)
    private String errorMessage;
}

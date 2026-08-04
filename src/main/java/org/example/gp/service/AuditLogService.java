package org.example.gp.service;

import jakarta.persistence.criteria.Predicate;
import org.example.gp.entity.AuditLog;
import org.example.gp.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Записва действие в одит лога. Изпълнява се асинхронно, за да не бави
     * основната заявка на потребителя.
     */
    @Async
    public void log(String username, Long officeId, String role,
                     String action, String httpMethod, String requestUri,
                     String details, String ipAddress,
                     boolean success, String errorMessage) {
        AuditLog entry = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .username(username != null ? username : "anonymous")
                .officeId(officeId)
                .role(role)
                .action(action)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .details(truncate(details, 2000))
                .ipAddress(ipAddress)
                .success(success)
                .errorMessage(truncate(errorMessage, 2000))
                .build();
        auditLogRepository.save(entry);
    }

    public Page<AuditLog> search(String username, String action, String ipAddress,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  Boolean successOnly, Pageable pageable) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("action")), "%" + action.toLowerCase() + "%"));
            }
            if (ipAddress != null && !ipAddress.isBlank()) {
                predicates.add(cb.like(root.get("ipAddress"), "%" + ipAddress + "%"));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), LocalDateTime.of(dateTo, LocalTime.MAX)));
            }
            if (successOnly != null) {
                predicates.add(cb.equal(root.get("success"), successOnly));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}

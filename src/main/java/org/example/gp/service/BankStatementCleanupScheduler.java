package org.example.gp.service;

import org.example.gp.repository.BankTransactionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Автоматично изтрива банкови транзакции, качени преди повече от 24 часа.
 *
 * Причина: банковите извлечения се качват без задължителна връзка с
 * конкретна фирма (тя се задава по-късно в прегледа) — ако останат по-дълго
 * необработени, лесно могат да се объркат/дублират между различни фирми.
 * 24 часа са достатъчни за преглед, корекция и износ; след това записът
 * трябва вече да е в Делта Про, а не в GDD.
 */
@Component
public class BankStatementCleanupScheduler {

    private final BankTransactionRepository repository;
    private final AuditLogService auditLogService;

    public BankStatementCleanupScheduler(BankTransactionRepository repository,
                                         AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Проверява на всеки час и трие транзакции, качени преди повече от 24 часа. */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupOldBankTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        List<org.example.gp.entity.BankTransaction> toDelete = repository.findByUploadedAtBefore(cutoff);
        if (toDelete.isEmpty()) {
            return;
        }

        int count = toDelete.size();
        repository.deleteByUploadedAtBefore(cutoff);

        auditLogService.log("system", null, null, "bank_statements.auto_cleanup", "SCHEDULED",
                "-", "Изтрити " + count + " банкови транзакции, качени преди повече от 24 часа", "-", true, null);
    }
}

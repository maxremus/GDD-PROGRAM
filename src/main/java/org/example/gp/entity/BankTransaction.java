package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Един ред от банково извлечение (плащане/постъпление), качен от CSV/Excel
 * файл, свален от онлайн банкирането. Преди износ към Делта Про минава
 * през преглед/корекция — автоматичното разпознаване на колони е "best
 * effort" и зависи от конкретния формат на всяка банка.
 */
@Entity
@Table(name = "bank_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long officeId;

    /** Фирмата, за която е плащането/постъплението (по избор). */
    private Long companyId;
    private String companyName;

    private LocalDate transactionDate;

    @Column(nullable = false)
    private BigDecimal amount; // винаги положително число

    @Enumerated(EnumType.STRING)
    private TransactionDirection direction; // IN = постъпление, OUT = плащане

    @Column(length = 500)
    private String description;

    private String counterpartyName;
    private String counterpartyIban;

    /** Дебитна сметка на счетоводния запис (напр. 401 при плащане, 503 при постъпление). */
    @Builder.Default
    private String debitAccount = "503";

    /** Кредитна сметка на счетоводния запис (напр. 503 при плащане, 411 при постъпление). */
    private String creditAccount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.NEW;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private String sourceFileName;
}

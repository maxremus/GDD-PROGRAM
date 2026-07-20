package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сканиран/снимков документ (фактура, касова бележка и т.н.), качен от
 * телефон или компютър. За момента само съхранение — без OCR.
 * Полетата ocrText/extractedData стоят готови за бъдещо автоматично
 * разпознаване и експорт към Микроинвест Делта Про.
 */
@Entity
@Table(name = "scanned_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long officeId;

    /** Фирмата, за която е документът (по избор — може да е null, ако не е избрана). */
    private Long companyId;

    /** Денормализирано ime на фирмата в момента на качване — за бърз преглед в списъка. */
    private String companyName;

    @Column(nullable = false)
    private String uploadedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] fileData;

    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.NEW;

    private LocalDateTime uploadedAt;

    /** За бъдещо OCR извличане — засега винаги null. */
    @Column(length = 4000)
    private String ocrText;

    // ==================== Полета за експорт към Микроинвест Делта Про ====================
    // Попълват се автоматично (грубо, чрез OCR) и се коригират ръчно преди експорт.

    /** Дата на документа (поле 2 в Import.txt) */
    private java.time.LocalDate documentDate;

    /** Номер на документа (поле 3) */
    private String documentNumber;

    /** Тип на документа: Ф-ра, ДИ, КИ, МД, Прот, ОП, ОПС, ПКО, РКО, МО, ББ, ПН (поле 4) */
    private String documentType;

    /** Тип на операцията/контировка — предефинирана (1-8) или сметки, напр. "304%-401" (поле 1) */
    @Builder.Default
    private String operationType = "1";

    /** Обща сума с ДДС (поле 5) */
    private java.math.BigDecimal totalAmount;

    /** Стойност на ДДС, -1 = автоматично изчисление (поле 16) */
    private java.math.BigDecimal vatAmount;

    /** Код на данъчна група 1-27 по спецификацията на Микроинвест (поле 6) */
    private Integer vatType;

    /** Ime на партньора (поле 7) */
    private String partnerName;

    /** МОЛ на партньора (поле 8) */
    private String partnerMol;

    /** Град на партньора (поле 9) */
    private String partnerCity;

    /** Адрес на партньора (поле 10) */
    private String partnerAddress;

    /** ДДС номер на партньора, напр. BG831826092 (поле 11) */
    private String partnerVatNumber;

    /** Булстат/ЕИК на партньора (поле 12) */
    private String partnerBulstat;

    /** Банка и сметка на партньора, свободен текст (поле 13) */
    private String bankAccount;

    /** Описание на сделката (поле 14) */
    @Column(length = 500)
    private String description;

    /** Общ брой листове/страници на документа (1 = само основната снимка). */
    @Builder.Default
    private int pageCount = 1;

    /** Бележки от AI анализа — напр. несъответствие в цените, липсваща ДДС регистрация и т.н. */
    @Column(length = 1000)
    private String warnings;
}

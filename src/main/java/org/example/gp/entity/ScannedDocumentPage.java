package org.example.gp.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Допълнителна страница (лист 2, 3...) на сканиран документ.
 * Първият лист се пази директно в ScannedDocument.fileData за обратна
 * съвместимост; тук отиват само страница 2 нагоре.
 */
@Entity
@Table(name = "scanned_document_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannedDocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scannedDocumentId;

    @Column(nullable = false)
    private Integer pageNumber; // 2, 3, 4...

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] fileData;
}

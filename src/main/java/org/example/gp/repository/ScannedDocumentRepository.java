package org.example.gp.repository;

import org.example.gp.entity.ScannedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScannedDocumentRepository extends JpaRepository<ScannedDocument, Long> {

    List<ScannedDocument> findByOfficeIdOrderByUploadedAtDesc(Long officeId);

    List<ScannedDocument> findByOfficeIdAndCompanyIdOrderByUploadedAtDesc(Long officeId, Long companyId);

    List<ScannedDocument> findAllByOrderByUploadedAtDesc(); // за ADMIN
}

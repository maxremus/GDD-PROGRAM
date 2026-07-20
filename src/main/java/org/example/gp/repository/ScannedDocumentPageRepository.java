package org.example.gp.repository;

import org.example.gp.entity.ScannedDocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScannedDocumentPageRepository extends JpaRepository<ScannedDocumentPage, Long> {

    List<ScannedDocumentPage> findByScannedDocumentIdOrderByPageNumberAsc(Long scannedDocumentId);

    void deleteByScannedDocumentId(Long scannedDocumentId);
}

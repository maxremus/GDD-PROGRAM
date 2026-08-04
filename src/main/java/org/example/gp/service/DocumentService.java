package org.example.gp.service;

import org.example.gp.entity.DocumentStatus;
import org.example.gp.entity.ScannedDocument;
import org.example.gp.entity.ScannedDocumentPage;
import org.example.gp.repository.ScannedDocumentPageRepository;
import org.example.gp.repository.ScannedDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class DocumentService {

    private static final int MAX_DIMENSION = 1600; // px — достатъчно за четлив документ, не пука базата
    private static final float JPEG_QUALITY = 0.8f;

    private final ScannedDocumentRepository repository;
    private final ScannedDocumentPageRepository pageRepository;

    public DocumentService(ScannedDocumentRepository repository, ScannedDocumentPageRepository pageRepository) {
        this.repository = repository;
        this.pageRepository = pageRepository;
    }

    /** Качва документ от 1 или повече снимки (листове). Първата снимка е "основната" за преглед в списъка. */
    public ScannedDocument saveScannedDocument(List<MultipartFile> files, Long officeId, Long companyId,
                                               String companyName, String uploadedBy, String note) throws IOException {

        if (files == null || files.isEmpty() || files.get(0).isEmpty()) {
            throw new IllegalArgumentException("Моля, изберете поне една снимка на документ.");
        }

        MultipartFile firstFile = files.get(0);
        String contentType = firstFile.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");

        byte[] data;
        String storedContentType;
        String fileName = firstFile.getOriginalFilename() != null ? firstFile.getOriginalFilename() : "document";

        if (isImage) {
            data = compressImage(firstFile);
            storedContentType = "image/jpeg";
            if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                fileName = fileName + ".jpg";
            }
        } else {
            data = firstFile.getBytes();
            storedContentType = contentType != null ? contentType : "application/octet-stream";
        }

        ScannedDocument doc = ScannedDocument.builder()
                .officeId(officeId)
                .companyId(companyId)
                .companyName(companyName)
                .uploadedBy(uploadedBy)
                .fileName(fileName)
                .contentType(storedContentType)
                .fileData(data)
                .note(note)
                .status(DocumentStatus.NEW)
                .uploadedAt(LocalDateTime.now())
                .pageCount(files.size())
                .build();

        doc = repository.save(doc);

        // Останалите листове (страница 2, 3...) — записваме ги свързани към основния документ
        for (int i = 1; i < files.size(); i++) {
            MultipartFile pageFile = files.get(i);
            if (pageFile == null || pageFile.isEmpty()) continue;

            String pageContentType = pageFile.getContentType();
            byte[] pageData;
            String storedPageContentType;

            if (pageContentType != null && pageContentType.startsWith("image/")) {
                pageData = compressImage(pageFile);
                storedPageContentType = "image/jpeg";
            } else {
                pageData = pageFile.getBytes();
                storedPageContentType = pageContentType != null ? pageContentType : "application/octet-stream";
            }

            ScannedDocumentPage page = ScannedDocumentPage.builder()
                    .scannedDocumentId(doc.getId())
                    .pageNumber(i + 1)
                    .contentType(storedPageContentType)
                    .fileData(pageData)
                    .build();
            pageRepository.save(page);
        }

        return doc;
    }

    public List<ScannedDocumentPage> getPages(Long documentId) {
        return pageRepository.findByScannedDocumentIdOrderByPageNumberAsc(documentId);
    }

    /** Смалява и компресира снимката до разумен размер (JPEG, макс. 1600px по дългата страна). */
    private byte[] compressImage(MultipartFile file) throws IOException {
        BufferedImage original = ImageIO.read(file.getInputStream());
        if (original == null) {
            // Не успяхме да декодираме като изображение — връщаме суровите байтове
            return file.getBytes();
        }

        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(width, height));

        BufferedImage resized;
        if (scale < 1.0) {
            int newWidth = (int) Math.round(width * scale);
            int newHeight = (int) Math.round(height * scale);
            resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
            g.dispose();
        } else {
            // Вече е достатъчно малка — само махаме алфа канал (JPEG не го поддържа)
            resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, Color.WHITE, null);
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(resized, null, null), param);
        } finally {
            writer.dispose();
        }

        return out.toByteArray();
    }

    public List<ScannedDocument> listForOffice(Long officeId) {
        if (officeId == null) {
            return repository.findAllByOrderByUploadedAtDesc(); // ADMIN вижда всичко
        }
        return repository.findByOfficeIdOrderByUploadedAtDesc(officeId);
    }

    public List<ScannedDocument> listForCompany(Long officeId, Long companyId) {
        return repository.findByOfficeIdAndCompanyIdOrderByUploadedAtDesc(officeId, companyId);
    }

    public ScannedDocument getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Документът не е намерен: " + id));
    }

    public ScannedDocument save(ScannedDocument document) {
        return repository.save(document);
    }

    public void delete(Long id, Long officeId) {
        ScannedDocument doc = getById(id);
        if (officeId != null && !officeId.equals(doc.getOfficeId())) {
            throw new RuntimeException("Нямате права да изтриете този документ.");
        }
        pageRepository.deleteByScannedDocumentId(id);
        repository.deleteById(id);
    }

    public void markStatus(Long id, Long officeId, DocumentStatus status) {
        ScannedDocument doc = getById(id);
        if (officeId != null && !officeId.equals(doc.getOfficeId())) {
            throw new RuntimeException("Нямате права за този документ.");
        }
        doc.setStatus(status);
        repository.save(doc);
    }
}

package org.example.gp.service;

import org.example.gp.entity.ScannedDocument;
import org.example.gp.repository.ScannedDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разпознава текст от снимка на документ чрез Tesseract OCR (безплатен,
 * self-hosted — виж Dockerfile) и прави грубо, евристично извличане на
 * основните счетоводни полета чрез regex. Извличането НЕ е перфектно —
 * винаги трябва да се прегледа и коригира от счетоводителя преди износ.
 */
@Service
public class OcrService {

    @Value("${ocr.tesseract-path:tesseract}")
    private String tesseractPath;

    @Value("${ocr.languages:bul}")
    private String languages;

    @Value("${ocr.enabled:true}")
    private boolean ocrEnabled;

    @Value("${ocr.tessdata-prefix:/usr/share/tessdata}")
    private String tessdataPrefix;

    @Value("${ocr.timeout-seconds:90}")
    private int timeoutSeconds;

    private final ScannedDocumentRepository repository;
    private final AuditLogService auditLogService;

    public OcrService(ScannedDocumentRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Извиква се асинхронно веднага след качване на документ (или ръчно за повторен опит). */
    @Async
    public void processDocumentAsync(Long documentId) {
        if (!ocrEnabled) return;

        ScannedDocument doc = repository.findById(documentId).orElse(null);
        if (doc == null || doc.getContentType() == null || !doc.getContentType().startsWith("image/")) {
            return; // PDF или друг формат — засега без OCR
        }

        try {
            TesseractResult result = runTesseract(doc.getFileData());

            if (result.text != null && !result.text.isBlank()) {
                doc.setOcrText(result.text);
                applyExtractedFields(doc, result.text);
                repository.save(doc);
                auditLogService.log("system", doc.getOfficeId(), null, "ocr.success", "OCR",
                        "documents/" + documentId, "разпознати " + result.text.length() + " символа", "-", true, null);
            } else {
                String errorMsg = "[OCR не разпозна текст] " +
                        (result.stderr != null && !result.stderr.isBlank() ? result.stderr.trim() : "празен резултат — проверете качеството на снимката.");
                doc.setOcrText(errorMsg);
                repository.save(doc);
                auditLogService.log("system", doc.getOfficeId(), null, "ocr.empty", "OCR",
                        "documents/" + documentId, errorMsg, "-", false, result.stderr);
            }

        } catch (Exception e) {
            try {
                doc.setOcrText("[OCR грешка] " + e.getMessage());
                repository.save(doc);
            } catch (Exception ignored) { }
            auditLogService.log("system", doc.getOfficeId(), null, "ocr.failed", "OCR",
                    "documents/" + documentId, "-", "-", false, e.getMessage());
        }
    }

    private static class TesseractResult {
        String text;
        String stderr;
    }

    private TesseractResult runTesseract(byte[] imageBytes) throws IOException, InterruptedException {
        File tempImage = File.createTempFile("ocr-", ".jpg");
        File tempOutBase = File.createTempFile("ocr-out-", "");
        tempOutBase.delete(); // tesseract сам ще създаде tempOutBase.txt

        TesseractResult result = new TesseractResult();

        try {
            Files.write(tempImage.toPath(), imageBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    tesseractPath, tempImage.getAbsolutePath(), tempOutBase.getAbsolutePath(),
                    "-l", languages
            );
            // Explicit TESSDATA_PREFIX — на Alpine tesseract понякога не намира
            // езиковите данни без него, дори да са инсталирани коректно.
            if (tessdataPrefix != null && !tessdataPrefix.isBlank()) {
                pb.environment().put("TESSDATA_PREFIX", tessdataPrefix);
            }
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            result.stderr = (stdout + "\n" + stderr).trim();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.stderr = "Timeout след " + timeoutSeconds + " секунди (сървърът е бавен за тази снимка).";
                return result;
            }

            File outputTxt = new File(tempOutBase.getAbsolutePath() + ".txt");
            if (outputTxt.exists()) {
                result.text = Files.readString(outputTxt.toPath());
                Files.deleteIfExists(outputTxt.toPath());
            }
            return result;

        } finally {
            tempImage.delete();
        }
    }

    // ==================== Евристично извличане на полета ====================

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[.\\-/](\\d{1,2})[.\\-/](\\d{4})\\b");
    private static final Pattern BULSTAT_PATTERN = Pattern.compile("\\b(\\d{9}|\\d{13})\\b");
    private static final Pattern VAT_NUMBER_PATTERN = Pattern.compile("\\bBG\\s?(\\d{9,10})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVOICE_NO_PATTERN = Pattern.compile(
            "(?:фактура|инвойс|№|номер)[^0-9]{0,10}(\\d{6,10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_NEAR_TOTAL = Pattern.compile(
            "(?:всичко|обща\\s*сума|дължим[а]?\\s*сума|за\\s*плащане|сума\\s*за\\s*плащане)[^0-9]{0,15}([0-9]+[.,][0-9]{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VAT_AMOUNT_PATTERN = Pattern.compile(
            "(?:ддс|vat)[^0-9]{0,15}([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);

    private void applyExtractedFields(ScannedDocument doc, String text) {
        // Дата
        Matcher dateM = DATE_PATTERN.matcher(text);
        if (dateM.find() && doc.getDocumentDate() == null) {
            try {
                int day = Integer.parseInt(dateM.group(1));
                int month = Integer.parseInt(dateM.group(2));
                int year = Integer.parseInt(dateM.group(3));
                doc.setDocumentDate(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        // ДДС номер на партньора
        Matcher vatNumM = VAT_NUMBER_PATTERN.matcher(text);
        if (vatNumM.find() && doc.getPartnerVatNumber() == null) {
            doc.setPartnerVatNumber("BG" + vatNumM.group(1));
        }

        // Булстат/ЕИК (9 или 13 цифри, различно от вече намерения ДДС номер)
        Matcher bulstatM = BULSTAT_PATTERN.matcher(text);
        if (bulstatM.find() && doc.getPartnerBulstat() == null) {
            doc.setPartnerBulstat(bulstatM.group(1));
        }

        // Номер на документ
        Matcher invM = INVOICE_NO_PATTERN.matcher(text);
        if (invM.find() && doc.getDocumentNumber() == null) {
            doc.setDocumentNumber(invM.group(1));
        }

        // Обща сума
        Matcher amountM = AMOUNT_NEAR_TOTAL.matcher(text);
        if (amountM.find() && doc.getTotalAmount() == null) {
            doc.setTotalAmount(parseAmount(amountM.group(1)));
        }

        // ДДС сума
        Matcher vatAmountM = VAT_AMOUNT_PATTERN.matcher(text);
        if (vatAmountM.find() && doc.getVatAmount() == null) {
            doc.setVatAmount(parseAmount(vatAmountM.group(1)));
        }

        // По подразбиране — фактура, ако нищо друго не е зададено
        if (doc.getDocumentType() == null) {
            doc.setDocumentType("Ф-ра");
        }
    }

    private BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}

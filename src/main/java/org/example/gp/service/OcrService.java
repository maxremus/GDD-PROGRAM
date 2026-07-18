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
    // ВАЖНО: Pattern.CASE_INSENSITIVE без Pattern.UNICODE_CASE НЕ работи за кирилица
    // (по подразбиране Java прави case-fold само за ASCII букви) — затова винаги
    // комбинираме двата флага, иначе "ФАКТУРА" (главни) никога не съвпада с "фактура".
    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[.\\-/](\\d{1,2})[.\\-/](\\d{4})\\b");
    private static final Pattern BULSTAT_PATTERN = Pattern.compile("\\b(\\d{9}|\\d{13})\\b");
    private static final Pattern VAT_NUMBER_PATTERN = Pattern.compile("\\bBG\\s?(\\d{9,10})\\b", CI);
    private static final Pattern MONEY_PATTERN = Pattern.compile("([0-9]{1,3}(?:[ .]?[0-9]{3})*[.,][0-9]{2})");

    private static final Pattern INVOICE_NO_LINE = Pattern.compile(
            "(?:фактура|инвойс|№|номер|no\\.?)[^0-9\\n]{0,15}(\\d{6,10})", CI);

    private static final Pattern TOTAL_LABEL_SPECIFIC = Pattern.compile(
            "(?:обща\\s*с[уy][мm]а|дължим[а]?\\s*с[уy][мm]а|за\\s*плащане|с[уy][мm]а\\s*за\\s*плащане)", CI);
    private static final Pattern TOTAL_LABEL_GENERIC = Pattern.compile("(?:^|\\s)(?:общо|всичко)", CI);
    private static final Pattern VAT_LABEL = Pattern.compile("(?:ддс|vat)(?!\\s*(?:номер|no|№))", CI);

    private static final Pattern SUPPLIER_LABEL = Pattern.compile("доставчик", CI);
    private static final Pattern MOL_LABEL = Pattern.compile("мол\\b\\.?:?", CI);
    private static final Pattern ADDRESS_LABEL = Pattern.compile("адрес\\b:?", CI);
    private static final Pattern COMPANY_SUFFIX = Pattern.compile("(ООД|ЕООД|АД|ЕАД|КД|СД|ЕТ)\\b", CI);

    /**
     * OCR (дори само с "bul" език) от време на време бърка визуално еднакви
     * латински/кирилски букви (напр. "cyma" вместо "сума"). Тук нормализираме
     * САМО за целите на откриване на etikети — 1 знак → 1 знак, за да могат
     * позициите да се ползват директно и в оригиналния (ненормализиран) ред.
     */
    private static String normalizeForLabelMatching(String line) {
        return line
                .replace('c', 'с').replace('C', 'С')
                .replace('y', 'у').replace('Y', 'У')
                .replace('o', 'о').replace('O', 'О')
                .replace('a', 'а').replace('A', 'А')
                .replace('e', 'е').replace('E', 'Е')
                .replace('x', 'х').replace('X', 'Х')
                .replace('p', 'р').replace('P', 'Р')
                .replace('H', 'Н')
                .replace('B', 'В')
                .replace('K', 'К')
                .replace('M', 'М')
                .replace('T', 'Т');
    }

    private void applyExtractedFields(ScannedDocument doc, String text) {
        String[] lines = text.split("\\r?\\n");

        // ── Дата ──
        Matcher dateM = DATE_PATTERN.matcher(text);
        if (dateM.find() && doc.getDocumentDate() == null) {
            try {
                int day = Integer.parseInt(dateM.group(1));
                int month = Integer.parseInt(dateM.group(2));
                int year = Integer.parseInt(dateM.group(3));
                doc.setDocumentDate(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        // ── ДДС номер / Булстат (винаги на латиница/цифри — не се нормализират) ──
        Matcher vatNumM = VAT_NUMBER_PATTERN.matcher(text);
        if (vatNumM.find() && doc.getPartnerVatNumber() == null) {
            doc.setPartnerVatNumber("BG" + vatNumM.group(1));
        }
        Matcher bulstatM = BULSTAT_PATTERN.matcher(text);
        if (bulstatM.find() && doc.getPartnerBulstat() == null) {
            doc.setPartnerBulstat(bulstatM.group(1));
        }

        // ── Номер на документ ──
        Matcher invM = INVOICE_NO_LINE.matcher(normalizeForLabelMatching(text));
        if (invM.find() && doc.getDocumentNumber() == null) {
            doc.setDocumentNumber(invM.group(1));
        }

        // ── Обща сума / ДДС / Партньор — ред по ред, съпоставяме върху нормализирана версия ──
        BigDecimal specificTotal = null;
        BigDecimal genericTotal = null;
        BigDecimal vatFound = null;
        String supplierName = null;
        String mol = null;
        String address = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String normLine = normalizeForLabelMatching(line);
            String nextLine = (i + 1 < lines.length) ? lines[i + 1].trim() : "";

            if (specificTotal == null && TOTAL_LABEL_SPECIFIC.matcher(normLine).find()) {
                BigDecimal amount = extractLastMoney(line);
                if (amount == null) amount = extractLastMoney(nextLine);
                if (amount != null) specificTotal = amount;
            }
            if (genericTotal == null && TOTAL_LABEL_GENERIC.matcher(normLine).find()) {
                BigDecimal amount = extractLastMoney(line);
                if (amount == null) amount = extractLastMoney(nextLine);
                if (amount != null) genericTotal = amount;
            }
            if (vatFound == null && VAT_LABEL.matcher(normLine).find()) {
                BigDecimal amount = extractLastMoney(line);
                if (amount == null) amount = extractLastMoney(nextLine);
                if (amount != null) vatFound = amount;
            }

            if (supplierName == null && SUPPLIER_LABEL.matcher(normLine).find()) {
                String candidate = textAfterLabel(line, normLine, SUPPLIER_LABEL);
                candidate = trimAtCompanySuffix(candidate);
                if (candidate == null || candidate.isBlank() || !COMPANY_SUFFIX.matcher(candidate).find()) {
                    String nextCandidate = trimAtCompanySuffix(nextLine);
                    if (nextCandidate != null && COMPANY_SUFFIX.matcher(nextCandidate).find()) candidate = nextCandidate;
                }
                if (candidate != null && !candidate.isBlank()) supplierName = candidate.trim();
            }

            if (mol == null && MOL_LABEL.matcher(normLine).find()) {
                String candidate = trimAtColumnBoundary(textAfterLabel(line, normLine, MOL_LABEL));
                if ((candidate == null || candidate.isBlank()) && !nextLine.isBlank()) candidate = trimAtColumnBoundary(nextLine);
                if (candidate != null && !candidate.isBlank()) mol = candidate.trim();
            }

            if (address == null && ADDRESS_LABEL.matcher(normLine).find()) {
                String candidate = trimAtColumnBoundary(textAfterLabel(line, normLine, ADDRESS_LABEL));
                if ((candidate == null || candidate.isBlank()) && !nextLine.isBlank()) candidate = trimAtColumnBoundary(nextLine);
                if (candidate != null && !candidate.isBlank()) address = candidate.trim();
            }
        }

        BigDecimal total = specificTotal != null ? specificTotal
                : genericTotal != null ? genericTotal
                : extractLargestMoney(text);

        if (total != null && doc.getTotalAmount() == null) doc.setTotalAmount(total);
        if (vatFound != null && doc.getVatAmount() == null) doc.setVatAmount(vatFound);
        if (supplierName != null && doc.getPartnerName() == null) doc.setPartnerName(supplierName);
        if (mol != null && doc.getPartnerMol() == null) doc.setPartnerMol(mol);
        if (address != null && doc.getPartnerAddress() == null) doc.setPartnerAddress(address);

        if (doc.getDocumentType() == null) {
            doc.setDocumentType("Ф-ра");
        }
    }

    /** Текстът след etikета — позицията се търси в нормализирания ред, но текстът се взима от оригинала. */
    private String textAfterLabel(String originalLine, String normalizedLine, Pattern labelPattern) {
        Matcher m = labelPattern.matcher(normalizedLine);
        if (!m.find()) return null;
        String rest = originalLine.substring(Math.min(m.end(), originalLine.length()));
        rest = rest.replaceFirst("^[:\\s]+", "").trim();
        return rest.isBlank() ? null : rest;
    }

    /** Отрязва при първата "|" (колонен разделител в OCR) или двоен интервал — за да не гребне целия ред. */
    private String trimAtColumnBoundary(String candidate) {
        if (candidate == null) return null;
        int cut = candidate.length();
        int pipeIdx = candidate.indexOf('|');
        if (pipeIdx >= 0) cut = Math.min(cut, pipeIdx);
        int doubleSpaceIdx = candidate.indexOf("  ");
        if (doubleSpaceIdx >= 0) cut = Math.min(cut, doubleSpaceIdx);
        cut = Math.min(cut, 60); // разумен таван за ime/адрес
        String trimmed = candidate.substring(0, cut).trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /** За ime на фирма — спира веднага след правната форма (ООД/ЕООД/...), за да отреже боклука след нея. */
    private String trimAtCompanySuffix(String candidate) {
        if (candidate == null) return null;
        String bounded = trimAtColumnBoundary(candidate);
        if (bounded == null) return null;
        Matcher suffixM = COMPANY_SUFFIX.matcher(bounded);
        if (suffixM.find()) {
            return bounded.substring(0, suffixM.end()).trim();
        }
        return bounded;
    }

    /** Последната парична сума на реда (обикновено сумата стои след етикета/валутата). */
    private BigDecimal extractLastMoney(String line) {
        Matcher m = MONEY_PATTERN.matcher(line);
        BigDecimal last = null;
        while (m.find()) {
            last = parseAmount(m.group(1));
        }
        return last;
    }

    /** Най-голямата парична сума в целия текст — груб fallback за "обща сума". */
    private BigDecimal extractLargestMoney(String text) {
        Matcher m = MONEY_PATTERN.matcher(text);
        BigDecimal max = null;
        while (m.find()) {
            BigDecimal value = parseAmount(m.group(1));
            if (value != null && (max == null || value.compareTo(max) > 0)) {
                max = value;
            }
        }
        return max;
    }

    private BigDecimal parseAmount(String raw) {
        try {
            String normalized = raw.replace(" ", "");
            if (normalized.contains(",") && normalized.contains(".")) {
                normalized = normalized.replace(",", "");
            } else {
                normalized = normalized.replace(",", ".");
            }
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return null;
        }
    }
}

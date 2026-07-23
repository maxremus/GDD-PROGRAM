package org.example.gp.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.gp.entity.BankTransaction;
import org.example.gp.entity.DocumentStatus;
import org.example.gp.entity.TransactionDirection;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Импортира банково извлечение от CSV или Excel (.xlsx) файл, свален от
 * онлайн банкиране. Разпознаването на колони е чрез гъвкаво съвпадение по
 * ime (candidate matching) — различните банки ползват различни заглавия,
 * затова резултатът винаги минава през преглед/корекция преди износ.
 */
@Service
public class BankStatementImportService {

    public static class ParseResult {
        public final List<BankTransaction> transactions = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
    };

    public ParseResult parse(MultipartFile file, Long officeId, String uploadedBy) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return parseExcel(file, officeId, uploadedBy);
        }
        return parseCsv(file, officeId, uploadedBy);
    }

    // ==================== CSV ====================

    private ParseResult parseCsv(MultipartFile file, Long officeId, String uploadedBy) throws IOException {
        ParseResult result = new ParseResult();
        byte[] bytes = file.getBytes();

        // Много български банки експортират CSV в Windows-1251, не UTF-8.
        Charset charset = detectCharset(bytes);
        List<String[]> rows = new ArrayList<>();
        char delimiter = detectDelimiter(bytes, charset);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(splitCsvLine(line, delimiter));
            }
        }

        if (rows.isEmpty()) {
            result.errors.add("Файлът е празен.");
            return result;
        }

        processRows(rows, officeId, uploadedBy, file.getOriginalFilename(), result);
        return result;
    }

    private Charset detectCharset(byte[] bytes) {
        // Груба евристика: ако декодирането като UTF-8 съдържа замяна-символи
        // на позиции, където очакваме кирилица, приемаме Windows-1251.
        String asUtf8 = new String(bytes, StandardCharsets.UTF_8);
        if (asUtf8.contains("\uFFFD")) {
            return Charset.forName("windows-1251");
        }
        return StandardCharsets.UTF_8;
    }

    private char detectDelimiter(byte[] bytes, Charset charset) {
        String sample = new String(bytes, 0, Math.min(bytes.length, 2000), charset);
        int firstLineEnd = sample.indexOf('\n');
        String firstLine = firstLineEnd > 0 ? sample.substring(0, firstLineEnd) : sample;
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long tabs = firstLine.chars().filter(c -> c == '\t').count();
        if (tabs > semicolons && tabs > commas) return '\t';
        return semicolons >= commas ? ';' : ',';
    }

    private String[] splitCsvLine(String line, char delimiter) {
        // Прост CSV split с поддръжка на кавички около полета
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    // ==================== Excel ====================

    private ParseResult parseExcel(MultipartFile file, Long officeId, String uploadedBy) throws IOException {
        ParseResult result = new ParseResult();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                result.errors.add("Файлът е празен или няма данни под заглавния ред.");
                return result;
            }

            DataFormatter formatter = new DataFormatter();
            List<String[]> rows = new ArrayList<>();
            int maxCol = 0;
            for (Row row : sheet) {
                maxCol = Math.max(maxCol, row.getLastCellNum());
            }

            for (Row row : sheet) {
                String[] values = new String[maxCol];
                for (int c = 0; c < maxCol; c++) {
                    values[c] = formatter.formatCellValue(row.getCell(c)).trim();
                }
                rows.add(values);
            }

            processRows(rows, officeId, uploadedBy, file.getOriginalFilename(), result);
        }

        return result;
    }

    // ==================== Обща логика по редове (CSV и Excel) ====================

    private static final String[] DATE_CANDIDATES = {"дата на осчетоводяване", "дата на валута", "вальор", "дата", "date"};
    private static final String[] AMOUNT_CANDIDATES = {"сума в лв", "сума в bgn", "сума", "amount"};
    private static final String[] CREDIT_CANDIDATES = {"кредит", "постъпление", "приход", "credit", "in"};
    private static final String[] DEBIT_CANDIDATES = {"дебит", "плащане", "разход", "debit", "out"};
    private static final String[] DESC_CANDIDATES = {"основание", "описание", "назначение", "текст на превода", "детайли", "description"};
    private static final String[] COUNTERPARTY_CANDIDATES = {"контрагент", "наредител/получател", "наредител", "получател", "ime на партньор", "partner"};
    private static final String[] IBAN_CANDIDATES = {"iban", "сметка на контрагент", "сметка контрагент"};

    private void processRows(List<String[]> rows, Long officeId, String uploadedBy, String sourceFileName, ParseResult result) {
        String[] header = rows.get(0);

        int colDate = findColumn(header, DATE_CANDIDATES);
        int colAmount = findColumn(header, AMOUNT_CANDIDATES);
        int colCredit = findColumn(header, CREDIT_CANDIDATES);
        int colDebit = findColumn(header, DEBIT_CANDIDATES);
        int colDesc = findColumn(header, DESC_CANDIDATES);
        int colCounterparty = findColumn(header, COUNTERPARTY_CANDIDATES);
        int colIban = findColumn(header, IBAN_CANDIDATES);

        if (colDate == -1 || (colAmount == -1 && colCredit == -1 && colDebit == -1)) {
            result.errors.add("Не намирам колони за дата и сума в заглавния ред. " +
                    "Проверете дали файлът има заглавен ред с имена на колони (напр. 'Дата', 'Сума' или 'Дебит'/'Кредит').");
            return;
        }

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (isRowBlank(row)) continue;

            LocalDate date = parseDate(get(row, colDate));
            if (date == null) {
                result.errors.add("Ред " + (r + 1) + ": невалидна/липсваща дата — пропуснат.");
                continue;
            }

            BigDecimal amount;
            TransactionDirection direction;

            if (colAmount != -1) {
                BigDecimal raw = parseAmount(get(row, colAmount));
                if (raw == null) {
                    result.errors.add("Ред " + (r + 1) + ": невалидна сума — пропуснат.");
                    continue;
                }
                direction = raw.signum() < 0 ? TransactionDirection.OUT : TransactionDirection.IN;
                amount = raw.abs();
            } else {
                BigDecimal creditVal = colCredit != -1 ? parseAmount(get(row, colCredit)) : null;
                BigDecimal debitVal = colDebit != -1 ? parseAmount(get(row, colDebit)) : null;
                if (creditVal != null && creditVal.signum() != 0) {
                    amount = creditVal.abs();
                    direction = TransactionDirection.IN;
                } else if (debitVal != null && debitVal.signum() != 0) {
                    amount = debitVal.abs();
                    direction = TransactionDirection.OUT;
                } else {
                    result.errors.add("Ред " + (r + 1) + ": няма стойност нито в дебит, нито в кредит колоната — пропуснат.");
                    continue;
                }
            }

            BankTransaction tx = BankTransaction.builder()
                    .officeId(officeId)
                    .transactionDate(date)
                    .amount(amount)
                    .direction(direction)
                    .description(colDesc != -1 ? get(row, colDesc) : null)
                    .counterpartyName(colCounterparty != -1 ? get(row, colCounterparty) : null)
                    .counterpartyIban(colIban != -1 ? get(row, colIban) : null)
                    // При плащане (OUT): Дебит 401 (доставчик) / Кредит 503 (банката).
                    // При постъпление (IN): Дебит 503 (банката) / Кредит 411 (клиент).
                    .debitAccount(direction == TransactionDirection.OUT ? "401" : "503")
                    .creditAccount(direction == TransactionDirection.OUT ? "503" : "411")
                    .status(DocumentStatus.NEW)
                    .uploadedBy(uploadedBy)
                    .uploadedAt(LocalDateTime.now())
                    .sourceFileName(sourceFileName)
                    .build();

            result.transactions.add(tx);
        }
    }

    private String get(String[] row, int idx) {
        if (idx == -1 || idx >= row.length) return null;
        String value = row[idx];
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private boolean isRowBlank(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) return false;
        }
        return true;
    }

    private int findColumn(String[] header, String[] candidates) {
        for (int i = 0; i < header.length; i++) {
            if (header[i] == null) continue;
            String value = header[i].trim().toLowerCase();
            for (String candidate : candidates) {
                if (value.equals(candidate) || value.contains(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (Exception ignored) { }
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String normalized = raw.replace(" ", "").replace("\u00A0", "");
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

package org.example.gp.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.gp.dto.CompanyImportDto;
import org.example.gp.entity.FilingStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Импорт на фирми от Excel (.xlsx) файл.
 * Очаквани колони в първия ред (header), в произволен ред, разпознати по ime:
 *   Ime | Година | Статистика | Чл.73 ал.1 | Чл.73 ал.6 | Годишна декларация | Декларация 6
 * Стойностите на статусните колони могат да са или на български
 * ("Подаване"/"Изчакване"/"Не се подава"), или техническото ime на enum-а
 * (SUBMITTED/PENDING/NOT_REQUIRED). Празна клетка = "Не се подава".
 */
@Service
public class ExcelImportService {

    private static final String[] HEADERS = {
            "Ime", "Година", "Статистика", "Чл.73 ал.1", "Чл.73 ал.6",
            "Годишна декларация", "Декларация 6"
    };

    public static class ParseResult {
        public final List<CompanyImportDto> companies = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    /** Парсва качения .xlsx файл в списък от CompanyImportDto. Редове с грешки се пропускат и се докладват. */
    public ParseResult parse(MultipartFile file) throws IOException {
        ParseResult result = new ParseResult();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                result.errors.add("Файлът е празен или няма данни под заглавния ред.");
                return result;
            }

            Row headerRow = sheet.getRow(0);
            int colName = findColumn(headerRow, "ime", "name", "фирма");
            int colYear = findColumn(headerRow, "година", "year");
            int colStatistics = findColumn(headerRow, "статистика", "statistics");
            int colCh73Al1 = findColumn(headerRow, "чл.73 ал.1", "чл 73 ал 1", "ch73al1");
            int colCh73Al6 = findColumn(headerRow, "чл.73 ал.6", "чл 73 ал 6", "ch73al6");
            int colAnnual = findColumn(headerRow, "годишна декларация", "annualdeclaration");
            int colDecl6 = findColumn(headerRow, "декларация 6", "declaration6");

            if (colName == -1 || colYear == -1) {
                result.errors.add("Не намирам задължителните колони 'Ime' и 'Година' в заглавния ред. " +
                        "Свалете шаблона за правилния формат.");
                return result;
            }

            DataFormatter formatter = new DataFormatter();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row, formatter)) {
                    continue; // прескачаме празни редове
                }

                String name = formatter.formatCellValue(row.getCell(colName)).trim();
                if (name.isBlank()) {
                    result.errors.add("Ред " + (r + 1) + ": липсва ime на фирма — пропуснат.");
                    continue;
                }

                Integer year = parseYear(formatter.formatCellValue(row.getCell(colYear)));
                if (year == null) {
                    result.errors.add("Ред " + (r + 1) + " (" + name + "): невалидна/липсваща година — пропуснат.");
                    continue;
                }

                CompanyImportDto dto = CompanyImportDto.builder()
                        .name(name)
                        .year(year)
                        .statistics(parseStatus(formatter.formatCellValue(getCell(row, colStatistics))))
                        .ch73Al1(parseStatus(formatter.formatCellValue(getCell(row, colCh73Al1))))
                        .ch73Al6(parseStatus(formatter.formatCellValue(getCell(row, colCh73Al6))))
                        .annualDeclaration(parseStatus(formatter.formatCellValue(getCell(row, colAnnual))))
                        .declaration6(parseStatus(formatter.formatCellValue(getCell(row, colDecl6))))
                        .build();

                result.companies.add(dto);
            }
        }

        return result;
    }

    /** Генерира примерен .xlsx шаблон с правилните заглавия и един примерен ред. */
    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Фирми");

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Примерна Фирма ЕООД");
            example.createCell(1).setCellValue(java.time.Year.now().getValue());
            example.createCell(2).setCellValue("Не се подава");
            example.createCell(3).setCellValue("Не се подава");
            example.createCell(4).setCellValue("Не се подава");
            example.createCell(5).setCellValue("Изчакване");
            example.createCell(6).setCellValue("Не се подава");

            Sheet infoSheet = workbook.createSheet("Инструкции");
            Row infoRow0 = infoSheet.createRow(0);
            infoRow0.createCell(0).setCellValue("Позволени стойности за статусните колони:");
            Row infoRow1 = infoSheet.createRow(1);
            infoRow1.createCell(0).setCellValue("Подаване / Изчакване / Не се подава (или празно = Не се подава)");
            infoSheet.setColumnWidth(0, 60 * 256);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Cell getCell(Row row, int col) {
        return col == -1 ? null : row.getCell(col);
    }

    private int findColumn(Row headerRow, String... candidates) {
        if (headerRow == null) return -1;
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim().toLowerCase();
            for (String candidate : candidates) {
                if (value.equals(candidate.toLowerCase()) || value.contains(candidate.toLowerCase())) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Integer parseYear(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FilingStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return FilingStatus.NOT_REQUIRED;
        }
        String v = raw.trim().toLowerCase();
        for (FilingStatus status : FilingStatus.values()) {
            if (status.name().equalsIgnoreCase(raw.trim()) || status.getLabel().toLowerCase().equals(v)) {
                return status;
            }
        }
        // Меки съвпадения на често срещани варианти
        if (v.contains("подава")) return FilingStatus.SUBMITTED;
        if (v.contains("изчак")) return FilingStatus.PENDING;
        return FilingStatus.NOT_REQUIRED;
    }
}

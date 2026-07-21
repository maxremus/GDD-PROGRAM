package org.example.gp.service;

import org.example.gp.entity.ScannedDocument;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Генерира Import.txt по официалната спецификация на Microinvest за обмен
 * на операции с Делта Про (Версия 5): текстов файл, Windows-1251, 16 полета
 * разделени с "|". Файлът се качва ръчно в Делта Про през менюто за импорт.
 *
 * Източник: https://microinvest.net/pub/integration%20-%20delta.pdf
 */
@Service
public class DocumentExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Charset CP1251 = Charset.forName("windows-1251");

    /** Генерира съдържанието на Import.txt (в CP1251 байтове) за подадените документи. */
    public byte[] generateImportFile(List<ScannedDocument> documents) {
        StringBuilder sb = new StringBuilder();

        for (ScannedDocument doc : documents) {
            sb.append(buildLine(doc)).append("\r\n");
        }

        return sb.toString().getBytes(CP1251);
    }

    private String buildLine(ScannedDocument doc) {
        String[] fields = new String[16];

        fields[0] = orDefault(doc.getOperationType(), "1");
        fields[1] = doc.getDocumentDate() != null ? doc.getDocumentDate().format(DATE_FMT) : "";
        fields[2] = padDocumentNumber(doc.getDocumentNumber());
        fields[3] = orDefault(doc.getDocumentType(), "Ф-ра");
        fields[4] = formatAmount(doc.getTotalAmount());
        fields[5] = doc.getVatType() != null ? String.valueOf(doc.getVatType()) : "";
        fields[6] = orEmpty(doc.getPartnerName());
        fields[7] = orEmpty(doc.getPartnerMol());
        fields[8] = orEmpty(doc.getPartnerCity());
        fields[9] = orEmpty(doc.getPartnerAddress());
        fields[10] = orEmpty(doc.getPartnerVatNumber());
        fields[11] = orEmpty(doc.getPartnerBulstat());
        fields[12] = orEmpty(doc.getBankAccount());
        fields[13] = orEmpty(doc.getDescription());
        fields[14] = orEmpty(doc.getNote());
        fields[15] = doc.getVatAmount() != null ? formatAmount(doc.getVatAmount()) : "-1";

        return String.join("|", fields);
    }

    private String padDocumentNumber(String number) {
        if (number == null || number.isBlank()) return "";
        String digitsOnly = number.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) return number;
        return String.format("%10s", digitsOnly).replace(' ', '0');
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

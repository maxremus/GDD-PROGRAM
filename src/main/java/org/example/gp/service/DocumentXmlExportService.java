package org.example.gp.service;

import org.example.gp.entity.BankTransaction;
import org.example.gp.entity.ScannedDocument;
import org.example.gp.entity.TransactionDirection;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Генерира XML в РЕАЛНИЯ формат на Делта Про за трансфер на счетоводни
 * операции ("TransferData", namespace urn:Transfer) — реверсно извлечен от
 * реален export, предоставен от потребителя, а не от остарялата 2008г.
 * pipe-delimited спецификация (Import.txt), която създаваше грешки.
 *
 * Ключова логика, потвърдена от реалния export:
 *   - ДДС регистрация на партньора: атрибутът VatNumber на <Company> се
 *     добавя САМО ако партньорът е регистриран по ДДС. Липсва изцяло,
 *     ако не е.
 *   - Обикновена покупка на стока = само 2 реда (Дебит 304 / Кредит 401),
 *     БЕЗ отделен ред за ДДС сметка 453 — Делта Про смята ДДС сама.
 */
@Service
public class DocumentXmlExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    public byte[] generateTransferXml(List<ScannedDocument> documents) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.newDocument();

        Element root = xml.createElementNS("urn:Transfer", "TransferData");
        xml.appendChild(root);

        Element accountingsEl = xml.createElement("Accountings");
        root.appendChild(accountingsEl);

        int number = 1;
        for (ScannedDocument doc : documents) {
            accountingsEl.appendChild(buildAccounting(xml, doc, number));
            number++;
        }

        return serialize(xml);
    }

    private Element buildAccounting(Document xml, ScannedDocument doc, int number) {
        Element accounting = xml.createElement("Accounting");

        String accountingDate = doc.getDocumentDate() != null
                ? doc.getDocumentDate().format(DATE_FMT)
                : java.time.LocalDate.now().format(DATE_FMT);

        accounting.setAttribute("AccountingDate", accountingDate);
        accounting.setAttribute("Number", padNumber(number));
        accounting.setAttribute("Reference", "");
        accounting.setAttribute("OptionalReference", "");
        accounting.setAttribute("Term", orEmpty(doc.getDescription()));
        accounting.setAttribute("Vies", "1");
        accounting.setAttribute("ViesMonth", accountingDate);

        // <Document> — номер и дата на самата фактура/бележка
        Element documentEl = xml.createElement("Document");
        documentEl.setAttribute("Date", accountingDate);
        documentEl.setAttribute("Number", orDefault(doc.getDocumentNumber(), "0"));
        documentEl.setAttribute("DocumentType", "1"); // 1 = Фактура (по образец от реален export)
        accounting.appendChild(documentEl);

        // <Company> — VatNumber се добавя САМО ако партньорът е регистриран по ДДС
        boolean isVatRegistered = doc.getPartnerVatNumber() != null && !doc.getPartnerVatNumber().isBlank();
        if (doc.getPartnerName() != null && !doc.getPartnerName().isBlank()) {
            Element companyEl = xml.createElement("Company");
            companyEl.setAttribute("Name", doc.getPartnerName());
            companyEl.setAttribute("Bulstat", orDefault(doc.getPartnerBulstat(), ""));
            if (isVatRegistered) {
                companyEl.setAttribute("VatNumber", doc.getPartnerVatNumber());
            }
            companyEl.appendChild(xml.createElement("BankAccounts"));
            accounting.appendChild(companyEl);
        }

        // <AccountingDetails> — сметките се вземат от полето "Операция/контировка" (напр. "304%-401"
        // или "501-401"), ако потребителят го е попълнил ръчно в прегледа. Иначе — автоматичен избор:
        // 304%-401 (с ДДС ред) при регистриран по ДДС партньор, 304-401 (без ДДС) иначе.
        String[] accounts = resolveAccounts(doc.getOperationType(), isVatRegistered);
        String debitAccount = accounts[0];
        String creditAccount = accounts[1];

        Element detailsEl = xml.createElement("AccountingDetails");

        String amount = formatAmount(doc.getTotalAmount());

        Element debit = xml.createElement("AccountingDetail");
        debit.setAttribute("AccountNumber", debitAccount);
        debit.setAttribute("Direction", "Debit");
        debit.setAttribute("VatTerm", "1");
        debit.setAttribute("Amount", amount);

        if (doc.getDescription() != null && !doc.getDescription().isBlank()) {
            Element materialDetail = xml.createElement("AccountingMaterialDetail");
            materialDetail.setAttribute("ProductName", doc.getDescription());
            // Реално количество/цена, ако са попълнени в прегледа; иначе разумен fallback:
            // количество 1 и ediнична цена = общата сума (по-добре от твърдо закодирани нули).
            BigDecimal qty = doc.getQuantity() != null ? doc.getQuantity() : BigDecimal.ONE;
            BigDecimal price = doc.getUnitPrice() != null ? doc.getUnitPrice()
                    : (doc.getTotalAmount() != null ? doc.getTotalAmount() : BigDecimal.ZERO);
            materialDetail.setAttribute("Price", formatAmount(price));
            materialDetail.setAttribute("Quantity", formatAmount(qty));
            debit.appendChild(materialDetail);
        }
        detailsEl.appendChild(debit);

        Element credit = xml.createElement("AccountingDetail");
        credit.setAttribute("AccountNumber", creditAccount);
        credit.setAttribute("Direction", "Credit");
        credit.setAttribute("VatTerm", "1");
        credit.setAttribute("Amount", amount);
        detailsEl.appendChild(credit);

        accounting.appendChild(detailsEl);

        return accounting;
    }

    /** Генерира TransferData XML за банкови транзакции (плащания/постъпления). */
    public byte[] generateBankTransferXml(List<BankTransaction> transactions) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.newDocument();

        Element root = xml.createElementNS("urn:Transfer", "TransferData");
        xml.appendChild(root);

        Element accountingsEl = xml.createElement("Accountings");
        root.appendChild(accountingsEl);

        int number = 1;
        for (BankTransaction tx : transactions) {
            accountingsEl.appendChild(buildBankAccounting(xml, tx, number));
            number++;
        }

        return serialize(xml);
    }

    private Element buildBankAccounting(Document xml, BankTransaction tx, int number) {
        Element accounting = xml.createElement("Accounting");

        String accountingDate = tx.getTransactionDate() != null
                ? tx.getTransactionDate().format(DATE_FMT)
                : java.time.LocalDate.now().format(DATE_FMT);

        accounting.setAttribute("AccountingDate", accountingDate);
        accounting.setAttribute("Number", padNumber(number));
        accounting.setAttribute("Reference", "");
        accounting.setAttribute("OptionalReference", "");
        accounting.setAttribute("Term", orEmpty(tx.getDescription()));
        accounting.setAttribute("Vies", "0");

        // <Company> — контрагентът по банковата транзакция (ако е разпознат)
        if (tx.getCounterpartyName() != null && !tx.getCounterpartyName().isBlank()) {
            Element companyEl = xml.createElement("Company");
            companyEl.setAttribute("Name", tx.getCounterpartyName());
            accounting.appendChild(companyEl);
        }

        // <AccountingDetails> — Дебит/Кредит според посоката:
        //   OUT (плащане): Дебит counterAccount (напр. 401) / Кредит ourAccount (503)
        //   IN  (постъпление): Дебит ourAccount (503) / Кредит counterAccount (напр. 411)
        Element detailsEl = xml.createElement("AccountingDetails");
        String amount = formatAmount(tx.getAmount());

        String debitAccount = tx.getDirection() == TransactionDirection.OUT
                ? orDefault(tx.getCounterAccount(), "401")
                : orDefault(tx.getOurAccount(), "503");
        String creditAccount = tx.getDirection() == TransactionDirection.OUT
                ? orDefault(tx.getOurAccount(), "503")
                : orDefault(tx.getCounterAccount(), "411");

        Element debit = xml.createElement("AccountingDetail");
        debit.setAttribute("AccountNumber", debitAccount);
        debit.setAttribute("Direction", "Debit");
        debit.setAttribute("Amount", amount);
        detailsEl.appendChild(debit);

        Element credit = xml.createElement("AccountingDetail");
        credit.setAttribute("AccountNumber", creditAccount);
        credit.setAttribute("Direction", "Credit");
        credit.setAttribute("Amount", amount);
        detailsEl.appendChild(credit);

        accounting.appendChild(detailsEl);

        return accounting;
    }

    /**
     * Парсва полето "Операция/контировка" (напр. "304%-401", "501-401") в
     * двойка [дебит сметка, кредит сметка]. Знакът "%" (ако е след дебит
     * сметката) е чисто визуален маркер за "с ДДС ред" в стария формат и
     * се маха, за да остане чист номер на сметка.
     * Ако полето е празно или е старото подразбиращо се "1" — избира
     * автоматично 304-401, според ДДС регистрацията на партньора.
     */
    private String[] resolveAccounts(String operationType, boolean isVatRegistered) {
        boolean hasCustomValue = operationType != null && !operationType.isBlank()
                && !operationType.equals("1");

        if (hasCustomValue && operationType.contains("-")) {
            String[] parts = operationType.split("-", 2);
            String debitPart = parts[0].trim();
            String creditPart = parts[1].trim();
            if (debitPart.endsWith("%")) {
                debitPart = debitPart.substring(0, debitPart.length() - 1).trim();
            }
            if (!debitPart.isEmpty() && !creditPart.isEmpty()) {
                return new String[]{debitPart, creditPart};
            }
        }

        // Автоматичен fallback
        return new String[]{"304", "401"};
    }

    private String padNumber(int number) {
        return String.format("%010d", number);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.000000";
        return amount.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value;
    }

    private byte[] serialize(Document xml) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(xml), new StreamResult(out));
        return out.toByteArray();
    }
}

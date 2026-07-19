package org.example.gp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.gp.entity.ScannedDocument;
import org.example.gp.repository.ScannedDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Разпознава документи (фактури, касови бележки — печатни И РЪКОПИСНИ) чрез
 * Google Gemini API. За разлика от класически OCR (Tesseract), Gemini е
 * мултимодален AI модел — разбира ръкописен текст и връща директно
 * структурирани данни (не се налага чуплив regex).
 *
 * Изисква безплатен API ключ от https://aistudio.google.com/apikey,
 * зададен като environment variable GEMINI_API_KEY.
 */
@Service
public class GeminiOcrService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${gemini.timeout-seconds:45}")
    private int timeoutSeconds;

    private final ScannedDocumentRepository repository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public GeminiOcrService(ScannedDocumentRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    private static final String PROMPT = """
            Ти си счетоводен асистент. Пред теб е снимка на българска фактура, касова бележка
            или друг счетоводен документ — текстът може да е ПЕЧАТЕН или РЪКОПИСЕН.
            Прочети внимателно всичко, включително ръкописен текст, подписи с изписани имена,
            и числа написани на ръка.

            Върни ЕДИНСТВЕНО валиден JSON (без markdown, без ```), с точно тези полета:
            {
              "rawText": "целият текст, който успя да прочетеш, ред по ред",
              "documentNumber": "номер на фактурата/документа или null",
              "documentDate": "дата във формат yyyy-MM-dd или null",
              "documentType": "едно от: Ф-ра, ДИ, КИ, МД, Прот, ОП, ОПС, ПКО, РКО, МО, ББ, ПН — по подразбиране Ф-ра",
              "totalAmount": число с точка (крайна сума с ДДС) или null,
              "vatAmount": число с точка (сума на ДДС) или null,
              "partnerName": "ime на фирмата-доставчик (не получателя)",
              "partnerMol": "МОЛ на доставчика или null",
              "partnerCity": "град на доставчика или null",
              "partnerAddress": "адрес на доставчика или null",
              "partnerVatNumber": "ДДС номер, напр. BG831826092, или null",
              "partnerBulstat": "Булстат/ЕИК (9 или 13 цифри) или null",
              "description": "кратко описание на стоката/услугата или null"
            }

            Ако не си сигурен в дадено поле, върни null за него, но НЕ пропускай полето.
            Числата винаги с точка като десетичен разделител (напр. 4163.67), без интервали и валутни знаци.
            """;

    @Async
    public void processDocumentAsync(Long documentId) {
        ScannedDocument doc = repository.findById(documentId).orElse(null);
        if (doc == null || doc.getContentType() == null || !doc.getContentType().startsWith("image/")) {
            return; // PDF или друг формат — засега без OCR
        }

        if (apiKey == null || apiKey.isBlank()) {
            doc.setOcrText("[OCR не е конфигуриран] Липсва GEMINI_API_KEY — виж https://aistudio.google.com/apikey");
            repository.save(doc);
            auditLogService.log("system", doc.getOfficeId(), null, "ocr.not_configured", "OCR",
                    "documents/" + documentId, "-", "-", false, "GEMINI_API_KEY не е зададен");
            return;
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(doc.getFileData());
            String requestBody = buildRequestBody(base64Image, doc.getContentType());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + apiKey))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errorMsg = "[OCR грешка] Gemini отговори с код " + response.statusCode() + ": "
                        + truncate(response.body(), 500);
                doc.setOcrText(errorMsg);
                repository.save(doc);
                auditLogService.log("system", doc.getOfficeId(), null, "ocr.failed", "OCR",
                        "documents/" + documentId, "-", "-", false, errorMsg);
                return;
            }

            String extractedJsonText = extractTextFromGeminiResponse(response.body());
            if (extractedJsonText == null || extractedJsonText.isBlank()) {
                doc.setOcrText("[OCR не разпозна текст] Празен отговор от Gemini.");
                repository.save(doc);
                auditLogService.log("system", doc.getOfficeId(), null, "ocr.empty", "OCR",
                        "documents/" + documentId, "-", "-", false, "празен отговор");
                return;
            }

            applyExtractedFields(doc, extractedJsonText);
            repository.save(doc);
            auditLogService.log("system", doc.getOfficeId(), null, "ocr.success", "OCR",
                    "documents/" + documentId, "Gemini разпозна документа успешно", "-", true, null);

        } catch (Exception e) {
            try {
                doc.setOcrText("[OCR грешка] " + e.getMessage());
                repository.save(doc);
            } catch (Exception ignored) { }
            auditLogService.log("system", doc.getOfficeId(), null, "ocr.failed", "OCR",
                    "documents/" + documentId, "-", "-", false, e.getMessage());
        }
    }

    private String buildRequestBody(String base64Image, String mimeType) throws Exception {
        var root = objectMapper.createObjectNode();
        var contents = root.putArray("contents");
        var content = contents.addObject();
        var parts = content.putArray("parts");

        var textPart = parts.addObject();
        textPart.put("text", PROMPT);

        var imagePart = parts.addObject();
        var inlineData = imagePart.putObject("inline_data");
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        // Искаме детерминиран, стриктен JSON отговор
        var generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");

        return objectMapper.writeValueAsString(root);
    }

    private String extractTextFromGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return null;

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return null;

        return parts.get(0).path("text").asText(null);
    }

    private void applyExtractedFields(ScannedDocument doc, String jsonText) {
        String cleaned = jsonText.trim();
        // Понякога моделът все пак обгражда с ```json ... ``` въпреки инструкцията — премахваме го
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }

        JsonNode data;
        try {
            data = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            // Не успяхме да парснем JSON — поне запазваме суровия текст, за да не изчезне
            doc.setOcrText("[OCR частична грешка] Отговорът не беше валиден JSON:\n" + cleaned);
            return;
        }

        doc.setOcrText(textOrNull(data, "rawText"));

        setIfBlank(doc::getDocumentNumber, doc::setDocumentNumber, textOrNull(data, "documentNumber"));
        setIfBlank(doc::getDocumentType, doc::setDocumentType,
                textOrDefault(data, "documentType", "Ф-ра"));
        setIfBlank(doc::getPartnerName, doc::setPartnerName, textOrNull(data, "partnerName"));
        setIfBlank(doc::getPartnerMol, doc::setPartnerMol, textOrNull(data, "partnerMol"));
        setIfBlank(doc::getPartnerCity, doc::setPartnerCity, textOrNull(data, "partnerCity"));
        setIfBlank(doc::getPartnerAddress, doc::setPartnerAddress, textOrNull(data, "partnerAddress"));
        setIfBlank(doc::getPartnerVatNumber, doc::setPartnerVatNumber, textOrNull(data, "partnerVatNumber"));
        setIfBlank(doc::getPartnerBulstat, doc::setPartnerBulstat, textOrNull(data, "partnerBulstat"));
        setIfBlank(doc::getDescription, doc::setDescription, textOrNull(data, "description"));

        if (doc.getDocumentDate() == null) {
            String rawDate = textOrNull(data, "documentDate");
            if (rawDate != null) {
                try {
                    doc.setDocumentDate(LocalDate.parse(rawDate));
                } catch (Exception ignored) { }
            }
        }

        if (doc.getTotalAmount() == null) {
            BigDecimal amount = numberOrNull(data, "totalAmount");
            if (amount != null) doc.setTotalAmount(amount);
        }
        if (doc.getVatAmount() == null) {
            BigDecimal amount = numberOrNull(data, "vatAmount");
            if (amount != null) doc.setVatAmount(amount);
        }
    }

    private interface Getter { String get(); }
    private interface Setter { void set(String value); }

    private void setIfBlank(Getter getter, Setter setter, String newValue) {
        if (newValue != null && !newValue.isBlank()
                && (getter.get() == null || getter.get().isBlank())) {
            setter.set(newValue);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return (text.isEmpty() || text.equalsIgnoreCase("null")) ? null : text;
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        String value = textOrNull(node, field);
        return value != null ? value : def;
    }

    private BigDecimal numberOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            if (value.isNumber()) return value.decimalValue();
            String text = value.asText().replace(",", ".").replaceAll("[^0-9.]", "");
            return text.isBlank() ? null : new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }
}

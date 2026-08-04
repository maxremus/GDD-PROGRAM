package org.example.gp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.gp.entity.BankTransaction;
import org.example.gp.entity.DocumentStatus;
import org.example.gp.entity.TransactionDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Разпознава банково извлечение от PDF файл чрез Gemini — много банки дават
 * извлечения само като PDF (не CSV/Excel), а PDF таблиците не се
 * извличат надеждно с прост текстов parser. Gemini чете PDF-а директно и
 * връща готов списък от транзакции.
 */
@Service
public class GeminiBankStatementService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-flash-latest}")
    private String model;

    @Value("${gemini.timeout-seconds:45}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final String PROMPT = """
            Пред теб е банково извлечение (PDF) от българска банка — списък с плащания и
            постъпления по банкова сметка. Прочети ВСИЧКИ редове/транзакции от документа
            (може да са на няколко страници).

            Върни ЕДИНСТВЕНО валиден JSON масив (без markdown, без ```), с точно тези полета
            за всяка транзакция:
            [
              {
                "date": "дата във формат yyyy-MM-dd",
                "amount": число с точка, ВИНАГИ положително,
                "direction": "IN" (постъпление, пари влизат) или "OUT" (плащане, пари излизат),
                "description": "основание/описание на транзакцията или null",
                "counterpartyName": "ime на контрагента (наредител при постъпление, получател при плащане) или null",
                "counterpartyIban": "IBAN на контрагента или null"
              }
            ]

            Ако дадена транзакция е без ясна дата или сума, пропусни я. Не измисляй данни.
            Числата винаги с точка като десетичен разделител, без интервали и валутни знаци.
            """;

    public static class ParseResult {
        public final List<BankTransaction> transactions = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    public ParseResult parse(MultipartFile file, Long officeId, String uploadedBy) throws Exception {
        ParseResult result = new ParseResult();

        if (apiKey == null || apiKey.isBlank()) {
            result.errors.add("OCR не е конфигуриран — липсва GEMINI_API_KEY. Виж https://aistudio.google.com/apikey");
            return result;
        }

        String requestBody = buildRequestBody(file);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent?key=" + apiKey))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            result.errors.add("Gemini отговори с код " + response.statusCode() + ": " + truncate(response.body(), 400));
            return result;
        }

        String jsonText = extractTextFromGeminiResponse(response.body());
        if (jsonText == null || jsonText.isBlank()) {
            result.errors.add("Празен отговор от Gemini.");
            return result;
        }

        parseTransactionsJson(jsonText, officeId, uploadedBy, file.getOriginalFilename(), result);
        return result;
    }

    private String buildRequestBody(MultipartFile file) throws Exception {
        var root = objectMapper.createObjectNode();
        var contents = root.putArray("contents");
        var content = contents.addObject();
        var parts = content.putArray("parts");

        var textPart = parts.addObject();
        textPart.put("text", PROMPT);

        var filePart = parts.addObject();
        var inlineData = filePart.putObject("inline_data");
        inlineData.put("mime_type", "application/pdf");
        inlineData.put("data", Base64.getEncoder().encodeToString(file.getBytes()));

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

    private void parseTransactionsJson(String jsonText, Long officeId, String uploadedBy,
                                       String sourceFileName, ParseResult result) {
        String cleaned = jsonText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").trim();
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        JsonNode array;
        try {
            array = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            result.errors.add("Отговорът от Gemini не беше валиден JSON: " + truncate(cleaned, 300));
            return;
        }

        if (!array.isArray()) {
            result.errors.add("Очаквах JSON масив от транзакции, получих друго.");
            return;
        }

        for (JsonNode node : array) {
            String dateStr = textOrNull(node, "date");
            LocalDate date = null;
            if (dateStr != null) {
                try { date = LocalDate.parse(dateStr); } catch (Exception ignored) { }
            }
            if (date == null) {
                result.errors.add("Транзакция без валидна дата — пропусната.");
                continue;
            }

            BigDecimal amount = numberOrNull(node, "amount");
            if (amount == null) {
                result.errors.add("Транзакция на " + dateStr + " без валидна сума — пропусната.");
                continue;
            }

            String directionStr = textOrNull(node, "direction");
            TransactionDirection direction = "OUT".equalsIgnoreCase(directionStr)
                    ? TransactionDirection.OUT : TransactionDirection.IN;

            BankTransaction tx = BankTransaction.builder()
                    .officeId(officeId)
                    .transactionDate(date)
                    .amount(amount.abs())
                    .direction(direction)
                    .description(textOrNull(node, "description"))
                    .counterpartyName(textOrNull(node, "counterpartyName"))
                    .counterpartyIban(textOrNull(node, "counterpartyIban"))
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

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return (text.isEmpty() || text.equalsIgnoreCase("null")) ? null : text;
    }

    private BigDecimal numberOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            if (value.isNumber()) return value.decimalValue();
            String text = value.asText().replace(",", ".").replaceAll("[^0-9.\\-]", "");
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

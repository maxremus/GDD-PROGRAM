package org.example.gp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Изпраща имейл известия (регистрация, смяна на парола, абонамент, backup и т.н.)
 * през Brevo HTTP API (https://api.brevo.com), НЕ през SMTP.
 *
 * ВАЖНО: Render блокира изходящите SMTP портове (25/465/587) за free tier
 * web services от 26.09.2025 г. — всеки опит за SMTP връзка увисва в timeout.
 * HTTPS API извикванията (порт 443) не са засегнати, затова минаваме през тях.
 *
 * Работи асинхронно и НИКОГА не хвърля изключение навън — грешка при
 * изпращане на имейл не трябва да чупи основното действие на потребителя.
 * Ако brevo.api-key не е зададен, изпращането се пропуска тихо.
 */
@Service
public class EmailService {

    private final TemplateEngine templateEngine;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.sender-email:}")
    private String senderEmail;

    @Value("${app.mail.from-name:GDD Program}")
    private String fromName;

    public EmailService(TemplateEngine templateEngine, AuditLogService auditLogService) {
        this.templateEngine = templateEngine;
        this.auditLogService = auditLogService;
    }

    /**
     * @param to           получател
     * @param subject      тема на писмото
     * @param templateName ime на HTML темплейт в resources/templates/email/ (без .html)
     * @param variables    променливи за темплейта
     */
    @Async
    public void send(String to, String subject, String templateName, Map<String, Object> variables) {
        sendInternal(to, subject, templateName, variables, null, null);
    }

    /** Изпраща имейл с прикачен файл (напр. backup архив на базата). */
    @Async
    public void sendWithAttachment(String to, String subject, String templateName,
                                    Map<String, Object> variables,
                                    File attachment, String attachmentName) {
        sendInternal(to, subject, templateName, variables, attachment, attachmentName);
    }

    private void sendInternal(String to, String subject, String templateName, Map<String, Object> variables,
                              File attachment, String attachmentName) {
        if (to == null || to.isBlank()) {
            return; // няма имейл на получателя — просто пропускаме
        }
        if (apiKey == null || apiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            // Brevo не е конфигуриран (напр. локална разработка) — пропускаме тихо, без грешка.
            auditLogService.log("system", null, null, "email.skipped", "MAIL", to,
                    subject + " (BREVO_API_KEY/BREVO_SENDER_EMAIL не е зададен)", "-", true, null);
            return;
        }

        try {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            String html = templateEngine.process("email/" + templateName, context);

            var root = objectMapper.createObjectNode();
            var sender = root.putObject("sender");
            sender.put("name", fromName);
            sender.put("email", senderEmail);

            var toArray = root.putArray("to");
            toArray.addObject().put("email", to);

            root.put("subject", subject);
            root.put("htmlContent", html);

            if (attachment != null && attachment.exists()) {
                var attachments = root.putArray("attachment");
                var att = attachments.addObject();
                att.put("content", Base64.getEncoder().encodeToString(Files.readAllBytes(attachment.toPath())));
                att.put("name", attachmentName != null ? attachmentName : attachment.getName());
            }

            String requestBody = objectMapper.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                auditLogService.log("system", null, null, "email.sent", "MAIL", to, subject, "-", true, null);
            } else {
                String errorMsg = "Brevo API код " + response.statusCode() + ": " + truncate(response.body(), 500);
                auditLogService.log("system", null, null, "email.failed", "MAIL", to, subject, "-", false, errorMsg);
            }

        } catch (Exception e) {
            auditLogService.log("system", null, null, "email.failed", "MAIL", to, subject, "-", false, e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }
}

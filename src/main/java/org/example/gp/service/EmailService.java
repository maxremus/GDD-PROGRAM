package org.example.gp.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Изпраща имейл известия (регистрация, смяна на парола, абонамент и т.н.).
 * Работи асинхронно и НИКОГА не хвърля изключение навън — грешка при
 * изпращане на имейл не трябва да чупи основното действие на потребителя.
 * Ако spring.mail.username не е зададен, изпращането се пропуска тихо
 * (полезно за локална разработка без SMTP акаунт).
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuditLogService auditLogService;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.mail.from-name:GDD Program}")
    private String fromName;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                         AuditLogService auditLogService) {
        this.mailSender = mailSender;
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
        if (to == null || to.isBlank()) {
            return; // няма имейл на получателя — просто пропускаме
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            // SMTP не е конфигуриран (напр. локална разработка) — пропускаме тихо, без грешка.
            auditLogService.log("system", null, null, "email.skipped", "MAIL", to,
                    subject + " (SMTP не е конфигуриран)", "-", true, null);
            return;
        }

        try {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            String html = templateEngine.process("email/" + templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom(fromAddress, fromName);

            mailSender.send(message);

            auditLogService.log("system", null, null, "email.sent", "MAIL", to, subject, "-", true, null);
        } catch (Exception e) {
            auditLogService.log("system", null, null, "email.failed", "MAIL", to, subject, "-", false, e.getMessage());
        }
    }

    /**
     * Изпраща имейл с прикачен файл (напр. backup архив на базата).
     */
    @Async
    public void sendWithAttachment(String to, String subject, String templateName,
                                    Map<String, Object> variables,
                                    java.io.File attachment, String attachmentName) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            auditLogService.log("system", null, null, "email.skipped", "MAIL", to,
                    subject + " (SMTP не е конфигуриран)", "-", true, null);
            return;
        }

        try {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            String html = templateEngine.process("email/" + templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom(fromAddress, fromName);
            if (attachment != null && attachment.exists()) {
                helper.addAttachment(attachmentName, attachment);
            }

            mailSender.send(message);

            auditLogService.log("system", null, null, "email.sent", "MAIL", to, subject, "-", true, null);
        } catch (Exception e) {
            auditLogService.log("system", null, null, "email.failed", "MAIL", to, subject, "-", false, e.getMessage());
        }
    }
}

package org.example.gp.controller;

import org.example.gp.service.AuditLogService;
import org.example.gp.service.BackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Достъпен само за системния администратор (виж SecurityConfig — /admin/** → ROLE_ADMIN).
 */
@Controller
public class BackupController {

    private final BackupService backupService;
    private final AuditLogService auditLogService;

    public BackupController(BackupService backupService, AuditLogService auditLogService) {
        this.backupService = backupService;
        this.auditLogService = auditLogService;
    }

    public record BackupRow(String filename, String size, String modified) {}

    @GetMapping("/admin/backups")
    public String backupsPage(Model model) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        List<BackupRow> rows = new ArrayList<>();
        for (Path p : backupService.listBackups()) {
            long size = Files.size(p);
            var modified = Files.getLastModifiedTime(p).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            rows.add(new BackupRow(p.getFileName().toString(),
                    BackupService.humanReadableSize(size), modified.format(fmt)));
        }
        model.addAttribute("backups", rows);
        model.addAttribute("backupDir", backupService.getBackupDir().toString());
        model.addAttribute("emailConfigured", backupService.isEmailNotificationConfigured());
        return "backups";
    }

    @PostMapping("/admin/backups/create")
    public String createBackup(RedirectAttributes redirectAttributes) {
        try {
            Path backup = backupService.createBackup();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Backup създаден успешно: " + backup.getFileName());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Грешка при създаване на backup: " + e.getMessage());
        }
        return "redirect:/admin/backups";
    }

    @PostMapping("/admin/backups/delete/{filename}")
    public String deleteBackup(@PathVariable String filename, RedirectAttributes redirectAttributes) {
        try {
            backupService.deleteBackup(filename);
            redirectAttributes.addFlashAttribute("successMessage", "Backup файлът е изтрит.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка: " + e.getMessage());
        }
        return "redirect:/admin/backups";
    }

    @GetMapping("/admin/backups/download/{filename}")
    public ResponseEntity<FileSystemResource> downloadBackup(@PathVariable String filename) {
        backupService.validateFilename(filename);
        Path file = backupService.getBackupDir().resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        auditLogService.log(username, null, null, "backup.download", "GET",
                "/admin/backups/download/" + filename, filename, "-", true, null);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new FileSystemResource(file));
    }
}

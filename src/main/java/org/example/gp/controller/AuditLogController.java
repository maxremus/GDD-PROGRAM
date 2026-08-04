package org.example.gp.controller;

import org.example.gp.entity.AuditLog;
import org.example.gp.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Достъпен само за системния администратор (виж SecurityConfig — /admin/** → ROLE_ADMIN).
 */
@Controller
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/audit-logs")
    public String auditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean successOnly,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        int size = 50;
        Page<AuditLog> result = auditLogService.search(
                username, action, ipAddress, dateFrom, dateTo, successOnly,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));

        model.addAttribute("logs", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("username", username);
        model.addAttribute("action", action);
        model.addAttribute("ipAddress", ipAddress);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("successOnly", successOnly);

        return "audit-logs";
    }
}

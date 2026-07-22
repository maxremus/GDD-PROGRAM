package org.example.gp.controller;

import org.example.gp.entity.BankTransaction;
import org.example.gp.entity.DocumentStatus;
import org.example.gp.entity.TransactionDirection;
import org.example.gp.entity.User;
import org.example.gp.repository.BankTransactionRepository;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.BankStatementImportService;
import org.example.gp.service.CompanyService;
import org.example.gp.service.DocumentXmlExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/bank-statements")
public class BankStatementController {

    private final BankStatementImportService importService;
    private final BankTransactionRepository repository;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final DocumentXmlExportService xmlExportService;

    public BankStatementController(BankStatementImportService importService,
                                   BankTransactionRepository repository,
                                   CompanyService companyService,
                                   UserRepository userRepository,
                                   DocumentXmlExportService xmlExportService) {
        this.importService = importService;
        this.repository = repository;
        this.companyService = companyService;
        this.userRepository = userRepository;
        this.xmlExportService = xmlExportService;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    private Long getCurrentOfficeId(User user) {
        if (user == null) return null;
        if ("ROLE_ADMIN".equals(user.getRole())) return null;
        return user.getOfficeId();
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "bank-statements-upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        try {
            BankStatementImportService.ParseResult parsed = importService.parse(
                    file, officeId, user != null ? user.getUsername() : "unknown");

            if (!parsed.transactions.isEmpty()) {
                repository.saveAll(parsed.transactions);
            }

            StringBuilder message = new StringBuilder();
            message.append("Разпознати транзакции: ").append(parsed.transactions.size()).append(".");
            if (!parsed.errors.isEmpty()) {
                message.append(" Пропуснати редове: ").append(parsed.errors.size()).append(".");
            }

            if (parsed.transactions.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Няма разпознати транзакции. " + String.join(" ", parsed.errors));
            } else if (!parsed.errors.isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        message + " Детайли: " + String.join(" ", parsed.errors));
            } else {
                redirectAttributes.addFlashAttribute("successMessage", message.toString());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка при четене на файла: " + e.getMessage());
        }

        return "redirect:/bank-statements";
    }

    @GetMapping
    public String list(Model model) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        List<BankTransaction> transactions = officeId == null
                ? repository.findAllByOrderByTransactionDateDesc()
                : repository.findByOfficeIdOrderByTransactionDateDesc(officeId);

        model.addAttribute("transactions", transactions);
        model.addAttribute("companies", companyService.getAllCompanies());
        return "bank-statements-list";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam(required = false) Long companyId,
                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate transactionDate,
                       @RequestParam BigDecimal amount,
                       @RequestParam TransactionDirection direction,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) String counterpartyName,
                       @RequestParam(required = false) String counterpartyIban,
                       @RequestParam(required = false) String ourAccount,
                       @RequestParam(required = false) String counterAccount,
                       RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        BankTransaction tx = repository.findById(id).orElse(null);
        if (tx == null || (officeId != null && !officeId.equals(tx.getOfficeId()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Транзакцията не е намерена или нямате права.");
            return "redirect:/bank-statements";
        }

        if (companyId != null) {
            tx.setCompanyId(companyId);
            companyService.getCompanyById(companyId).ifPresent(c -> tx.setCompanyName(c.getName()));
        }
        tx.setTransactionDate(transactionDate);
        tx.setAmount(amount);
        tx.setDirection(direction);
        tx.setDescription(description);
        tx.setCounterpartyName(counterpartyName);
        tx.setCounterpartyIban(counterpartyIban);
        tx.setOurAccount(ourAccount != null && !ourAccount.isBlank() ? ourAccount : "503");
        tx.setCounterAccount(counterAccount);
        tx.setStatus(DocumentStatus.REVIEWED);

        repository.save(tx);
        redirectAttributes.addFlashAttribute("successMessage", "Транзакцията е запазена.");
        return "redirect:/bank-statements";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        BankTransaction tx = repository.findById(id).orElse(null);
        if (tx == null || (officeId != null && !officeId.equals(tx.getOfficeId()))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Транзакцията не е намерена или нямате права.");
        } else {
            repository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Транзакцията е изтрита.");
        }
        return "redirect:/bank-statements";
    }

    @GetMapping("/export-xml")
    @ResponseBody
    public ResponseEntity<byte[]> exportXml(@RequestParam List<Long> ids) throws Exception {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        List<BankTransaction> toExport = new ArrayList<>();
        for (Long id : ids) {
            repository.findById(id).ifPresent(tx -> {
                if (officeId == null || officeId.equals(tx.getOfficeId())) {
                    toExport.add(tx);
                }
            });
        }

        byte[] content = xmlExportService.generateBankTransferXml(toExport);

        for (BankTransaction tx : toExport) {
            tx.setStatus(DocumentStatus.EXPORTED);
            repository.save(tx);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bank-transfer.xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(content);
    }
}

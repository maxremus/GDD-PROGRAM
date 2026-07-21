package org.example.gp.controller;

import org.example.gp.entity.Company;
import org.example.gp.entity.DocumentStatus;
import org.example.gp.entity.ScannedDocument;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.CompanyService;
import org.example.gp.service.DocumentExportService;
import org.example.gp.service.DocumentService;
import org.example.gp.service.DocumentXmlExportService;
import org.example.gp.service.GeminiOcrService;
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
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final GeminiOcrService ocrService;
    private final DocumentExportService documentExportService;
    private final DocumentXmlExportService documentXmlExportService;

    public DocumentController(DocumentService documentService,
                              CompanyService companyService,
                              UserRepository userRepository,
                              GeminiOcrService ocrService,
                              DocumentExportService documentExportService,
                              DocumentXmlExportService documentXmlExportService) {
        this.documentService = documentService;
        this.companyService = companyService;
        this.userRepository = userRepository;
        this.ocrService = ocrService;
        this.documentExportService = documentExportService;
        this.documentXmlExportService = documentXmlExportService;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    private Long getCurrentOfficeId(User user) {
        if (user == null) return null;
        if ("ROLE_ADMIN".equals(user.getRole())) return null; // системен admin вижда всичко
        return user.getOfficeId();
    }

    @GetMapping("/scan")
    public String scanPage(Model model) {
        model.addAttribute("companies", companyService.getAllCompanies());
        return "documents-scan";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam(required = false) Long companyId,
                         @RequestParam(required = false) String note,
                         RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        String companyName = null;
        if (companyId != null) {
            companyName = companyService.getCompanyById(companyId)
                    .map(Company::getName)
                    .orElse(null);
        }

        try {
            ScannedDocument saved = documentService.saveScannedDocument(files, officeId, companyId, companyName,
                    user != null ? user.getUsername() : "unknown", note);
            ocrService.processDocumentAsync(saved.getId());
            String pagesMsg = files.size() > 1 ? " (" + files.size() + " листа)" : "";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Документът" + pagesMsg + " е качен успешно. Разпознаването на текста (OCR) тече във фонов режим — презаредете списъка след няколко секунди.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка при качване: " + e.getMessage());
        }

        return "redirect:/documents";
    }

    @GetMapping
    public String listDocuments(@RequestParam(required = false) Long companyId, Model model) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        List<ScannedDocument> documents = (companyId != null)
                ? documentService.listForCompany(officeId, companyId)
                : documentService.listForOffice(officeId);

        model.addAttribute("documents", documents);
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("selectedCompanyId", companyId);
        return "documents-list";
    }

    @GetMapping("/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> viewImage(@PathVariable Long id) {
        ScannedDocument doc = documentService.getById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(doc.getFileData());
    }

    @GetMapping("/{id}/page/{pageNumber}")
    @ResponseBody
    public ResponseEntity<byte[]> viewPage(@PathVariable Long id, @PathVariable int pageNumber) {
        var page = documentService.getPages(id).stream()
                .filter(p -> p.getPageNumber() == pageNumber)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Страницата не е намерена"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(page.getContentType()))
                .body(page.getFileData());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);
        try {
            documentService.delete(id, officeId);
            redirectAttributes.addFlashAttribute("successMessage", "Документът е изтрит.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam DocumentStatus status,
                               RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);
        try {
            documentService.markStatus(id, officeId, status);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, Model model) {
        model.addAttribute("doc", documentService.getById(id));
        model.addAttribute("pages", documentService.getPages(id));
        return "documents-edit";
    }

    @PostMapping("/{id}/retry-ocr")
    public String retryOcr(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ocrService.processDocumentAsync(id);
        redirectAttributes.addFlashAttribute("successMessage", "OCR стартиран отново — презаредете след няколко секунди.");
        return "redirect:/documents/" + id + "/edit";
    }

    @PostMapping("/{id}/edit")
    public String saveEdit(@PathVariable Long id,
                           @RequestParam(required = false) String documentType,
                           @RequestParam(required = false) String operationType,
                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate documentDate,
                           @RequestParam(required = false) String documentNumber,
                           @RequestParam(required = false) BigDecimal totalAmount,
                           @RequestParam(required = false) BigDecimal vatAmount,
                           @RequestParam(required = false) Integer vatType,
                           @RequestParam(required = false) String partnerName,
                           @RequestParam(required = false) String partnerMol,
                           @RequestParam(required = false) String partnerCity,
                           @RequestParam(required = false) String partnerAddress,
                           @RequestParam(required = false) String partnerVatNumber,
                           @RequestParam(required = false) String partnerBulstat,
                           @RequestParam(required = false) String bankAccount,
                           @RequestParam(required = false) String description,
                           RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        ScannedDocument doc = documentService.getById(id);
        if (officeId != null && !officeId.equals(doc.getOfficeId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Нямате права за този документ.");
            return "redirect:/documents";
        }

        doc.setDocumentType(documentType);
        doc.setOperationType(operationType != null && !operationType.isBlank() ? operationType : "1");
        doc.setDocumentDate(documentDate);
        doc.setDocumentNumber(documentNumber);
        doc.setTotalAmount(totalAmount);
        doc.setVatAmount(vatAmount);
        doc.setVatType(vatType);
        doc.setPartnerName(partnerName);
        doc.setPartnerMol(partnerMol);
        doc.setPartnerCity(partnerCity);
        doc.setPartnerAddress(partnerAddress);
        doc.setPartnerVatNumber(partnerVatNumber);
        doc.setPartnerBulstat(partnerBulstat);
        doc.setBankAccount(bankAccount);
        doc.setDescription(description);
        doc.setStatus(DocumentStatus.REVIEWED);

        documentService.save(doc);

        redirectAttributes.addFlashAttribute("successMessage", "Данните са запазени. Документът е маркиран като прегледан.");
        return "redirect:/documents";
    }

    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportSelected(@RequestParam List<Long> ids) {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        List<ScannedDocument> toExport = new ArrayList<>();
        for (Long id : ids) {
            ScannedDocument doc = documentService.getById(id);
            if (officeId == null || officeId.equals(doc.getOfficeId())) {
                toExport.add(doc);
            }
        }

        byte[] content = documentExportService.generateImportFile(toExport);

        for (ScannedDocument doc : toExport) {
            documentService.markStatus(doc.getId(), officeId, DocumentStatus.EXPORTED);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Import.txt\"")
                .contentType(MediaType.parseMediaType("text/plain; charset=windows-1251"))
                .body(content);
    }

    @GetMapping("/export-xml")
    @ResponseBody
    public ResponseEntity<byte[]> exportSelectedXml(@RequestParam List<Long> ids) throws Exception {
        User user = getCurrentUser();
        Long officeId = getCurrentOfficeId(user);

        List<ScannedDocument> toExport = new ArrayList<>();
        for (Long id : ids) {
            ScannedDocument doc = documentService.getById(id);
            if (officeId == null || officeId.equals(doc.getOfficeId())) {
                toExport.add(doc);
            }
        }

        byte[] content = documentXmlExportService.generateTransferXml(toExport);

        for (ScannedDocument doc : toExport) {
            documentService.markStatus(doc.getId(), officeId, DocumentStatus.EXPORTED);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transfer.xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(content);
    }
}

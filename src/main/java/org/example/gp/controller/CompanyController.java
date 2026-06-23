package org.example.gp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.gp.dto.CompanyImportDto;
import org.example.gp.entity.Company;
import org.example.gp.entity.CompanyWorked;
import org.example.gp.entity.FilingStatus;
import org.example.gp.entity.FilingStatusMore;
import org.example.gp.repository.CompanyRepository;
import org.example.gp.repository.CompanyWorkedRepository;
import org.example.gp.service.CompanyService;
import org.example.gp.service.CompanyWorkedService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final CompanyWorkedRepository companyWorkedRepository;
    private final CompanyWorkedService companyWorkedService;

    public CompanyController(CompanyService companyService,
                             CompanyRepository companyRepository,
                             CompanyWorkedRepository companyWorkedRepository,
                             CompanyWorkedService companyWorkedService) {
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.companyWorkedRepository = companyWorkedRepository;
        this.companyWorkedService = companyWorkedService;
    }

    // -------------------------------------------------------------------------
    // Само ADMIN може да добавя, редактира, трие, качва фирми
    // -------------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/add")
    public ModelAndView addCompany(@ModelAttribute Company company,
                                   RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");
        try {
            companyService.createCompany(company);
            redirectAttributes.addFlashAttribute("successMessage", "Компанията е добавена успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка при добавяне: " + e.getMessage());
        }
        return mav;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/update")
    public ModelAndView updateCompany(@ModelAttribute Company company,
                                      RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");
        try {
            companyService.updateCompany(company);
            redirectAttributes.addFlashAttribute("successMessage", "Компанията е актуализирана успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка при актуализиране: " + e.getMessage());
        }
        return mav;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/delete/{id}")
    public ModelAndView deleteCompany(@PathVariable Long id,
                                      RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");
        try {
            companyService.deleteCompany(id);
            redirectAttributes.addFlashAttribute("successMessage", "Компанията е изтрита успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Грешка при изтриване: " + e.getMessage());
        }
        return mav;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/copy")
    public String copyToNextYear(@RequestParam String currentYear,
                                 @RequestParam String nextYear) {
        companyService.copyCompaniesToNextYear(currentYear, nextYear);
        return "redirect:/companies";
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/upload")
    public String uploadCompanies(@RequestParam("file") MultipartFile file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Company> companies = mapper.readValue(file.getInputStream(),
                    new TypeReference<List<Company>>() {});
            for (Company company : companies) {
                company.setStatistics(FilingStatus.NOT_REQUIRED);
                company.setCh73Al1(FilingStatus.NOT_REQUIRED);
                company.setCh73Al6(FilingStatus.NOT_REQUIRED);
                company.setAnnualDeclaration(FilingStatus.NOT_REQUIRED);
                company.setDeclaration6(FilingStatus.NOT_REQUIRED);
                companyRepository.save(company);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/companies";
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/import")
    public String importCompanies(@RequestParam("file") MultipartFile file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<CompanyImportDto> companies = mapper.readValue(file.getInputStream(),
                    new TypeReference<List<CompanyImportDto>>() {});
            companyService.importCompanies(companies);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/companies";
    }

    // -------------------------------------------------------------------------
    // GET /companies — филтрирането се случва в CompanyImp.getAllCompanies()
    // -------------------------------------------------------------------------

    @GetMapping
    public ModelAndView listCompanies(
            @RequestParam(required = false) Long editId,
            @RequestParam(required = false) String search) {

        ModelAndView mav = new ModelAndView("index");
        List<Company> companies;

        if (search != null && !search.isEmpty()) {
            companies = (List<Company>) companyService.searchCompanies(search);
        } else {
            companies = companyService.getAllCompanies();
        }

        mav.addObject("companies", companies);
        mav.addObject("search", search);

        if (editId != null) {
            companyRepository.findById(editId)
                    .ifPresent(c -> mav.addObject("editingCompany", c));
        }

        return mav;
    }

    // -------------------------------------------------------------------------
    // GET /companies/worked — ADMIN вижда всички, USER/READONLY само своите
    // -------------------------------------------------------------------------

    @GetMapping("/worked")
    public ModelAndView workedPage(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String search) {

        ModelAndView mav = new ModelAndView("worked");

        if (month == null || year == null) {
            LocalDate now = LocalDate.now();
            month = now.getMonthValue();
            year = now.getYear();
        }

        // getAllCompanies() вече е филтриран по роля
        List<Company> allCompanies = companyService.getAllCompanies();

        if (search != null && !search.isEmpty()) {
            final String s = search.toLowerCase();
            allCompanies = allCompanies.stream()
                    .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(s))
                    .toList();
        }

        List<CompanyWorked> workedList = companyWorkedRepository.findAll();
        Map<Long, CompanyWorked> workedMap = new HashMap<>();

        for (CompanyWorked w : workedList) {
            if (w.getMonth().equals(month) && w.getYear().equals(year)) {
                workedMap.put(w.getCompany().getId(), w);
            }
        }

        List<Company> result = new ArrayList<>();
        for (Company c : allCompanies) {
            Company temp = new Company();
            temp.setId(c.getId());
            temp.setName(c.getName());

            if (workedMap.containsKey(c.getId())) {
                CompanyWorked w = workedMap.get(c.getId());
                temp.setStatistics2(w.getStatus());
                temp.setWorkedId(w.getId());
            } else {
                temp.setStatistics2(FilingStatusMore.EMPTY);
                temp.setWorkedId(null);
            }

            result.add(temp);
        }

        mav.addObject("companies", result);
        mav.addObject("selectedMonth", month);
        mav.addObject("selectedYear", year);
        mav.addObject("search", search);
        mav.addObject("statuses", FilingStatusMore.values());

        return mav;
    }

    // ADMIN и USER могат да обновяват статус; READONLY — не
    @PreAuthorize("hasAnyRole('ADMIN','OFFICE','USER')")
    @PostMapping("/update-worked")
    public String updateWorked(@RequestParam Long id,
                               @RequestParam FilingStatusMore status,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {

        Company company = companyRepository.findById(id).orElseThrow();
        Optional<CompanyWorked> existing =
                companyWorkedRepository.findByCompanyAndMonthAndYear(company, month, year);

        CompanyWorked w = existing.orElseGet(() -> {
            CompanyWorked newW = new CompanyWorked();
            newW.setCompany(company);
            newW.setMonth(month);
            newW.setYear(year);
            return newW;
        });

        w.setStatus(status);
        companyWorkedRepository.save(w);

        return "redirect:/companies/worked?month=" + month + "&year=" + year;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/copy-month")
    public String copyMonth(@RequestParam Integer month, @RequestParam Integer year) {
        int nextMonth = month + 1;
        int nextYear = year;
        if (nextMonth > 12) { nextMonth = 1; nextYear++; }
        return "redirect:/companies/worked?month=" + nextMonth + "&year=" + nextYear;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OFFICE')")
    @PostMapping("/worked/delete/{id}")
    public String deleteWorked(@PathVariable Long id,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {
        companyWorkedService.deleteById(id);
        return "redirect:/companies/worked?month=" + month + "&year=" + year;
    }
}

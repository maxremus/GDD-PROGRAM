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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final CompanyWorkedRepository companyWorkedRepository;
    private final CompanyWorkedService companyWorkedService;

    public CompanyController(CompanyService companyService, CompanyRepository companyRepository, CompanyWorkedRepository companyWorkedRepository, CompanyWorkedService companyWorkedService) {
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.companyWorkedRepository = companyWorkedRepository;
        this.companyWorkedService = companyWorkedService;
    }


    @PostMapping("/add")
    public ModelAndView addCompany(@ModelAttribute Company company,
                                   RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");

        try {
            companyService.createCompany(company);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Компанията е добавена успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Грешка при добавяне на компания: " + e.getMessage());
        }

        return mav;
    }

    @PostMapping("/update")
    public ModelAndView updateCompany(@ModelAttribute Company company,
                                      RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");

        try {
            companyService.updateCompany(company);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Компанията е актуализирана успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Грешка при актуализиране на компания: " + e.getMessage());
        }

        return mav;
    }

    @PostMapping("/delete/{id}")
    public ModelAndView deleteCompany(@PathVariable Long id,
                                      RedirectAttributes redirectAttributes) {
        ModelAndView mav = new ModelAndView("redirect:/companies");

        try {
            companyService.deleteCompany(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Компанията е изтрита успешно!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Грешка при изтриване на компания: " + e.getMessage());
        }

        return mav;
    }

    @PostMapping("/companies/copy")
    public String copyToNextYear(@RequestParam String currentYear,
                                 @RequestParam String nextYear) {

        companyService.copyCompaniesToNextYear(currentYear, nextYear);
        return "redirect:/companies";
    }

    @PostMapping("/upload")
    public String uploadCompanies(@RequestParam("file") MultipartFile file) {

        try {
            ObjectMapper mapper = new ObjectMapper();

            List<Company> companies = mapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<Company>>() {}
            );

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

    @PostMapping("/import")
    public String importCompanies(@RequestParam("file") MultipartFile file) {

        try {
            ObjectMapper mapper = new ObjectMapper();

            List<CompanyImportDto> companies =
                    mapper.readValue(file.getInputStream(),
                            new TypeReference<List<CompanyImportDto>>() {});

            companyService.importCompanies(companies);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "redirect:/companies";
    }

    @GetMapping
    public ModelAndView listCompanies(
            @RequestParam(required = false) Long editId,
            @RequestParam(required = false) String search
    ) {
        ModelAndView mav = new ModelAndView("index");

        List<Company> companies;

        if (search != null && !search.isEmpty()) {
            companies = (List<Company>) companyService.searchCompanies(search);
        } else {
            companies = companyService.getAllCompanies();
        }

        mav.addObject("companies", companies);
        mav.addObject("search", search);

        // FIX за editingCompany
        if (editId != null) {
            Optional<Company> companyOpt = companyRepository.findById(editId);

            companyOpt.ifPresent(company ->
                    mav.addObject("editingCompany", company)
            );
        }

        return mav;
    }


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

        //  всички фирми
        List<Company> allCompanies = companyRepository.findAll();

        //  search
        if (search != null && !search.isEmpty()) {
            allCompanies = allCompanies.stream()
                    .filter(c -> c.getName() != null &&
                            c.getName().toLowerCase().contains(search.toLowerCase()))
                    .toList();
        }

        //  записите за текущия месец (НОВАТА таблица)
        List<CompanyWorked> workedList =
                companyWorkedRepository.findAll();

        //  map: companyId → worked
        Map<Long, CompanyWorked> workedMap = new HashMap<>();

        for (CompanyWorked w : workedList) {
            if (w.getMonth().equals(month) && w.getYear().equals(year)) {
                workedMap.put(w.getCompany().getId(), w);
            }
        }

        //  резултат
        List<Company> result = new ArrayList<>();

        for (Company c : allCompanies) {

            if (workedMap.containsKey(c.getId())) {
                // има статус
                CompanyWorked w = workedMap.get(c.getId());

                Company temp = new Company();
                temp.setId(c.getId());
                temp.setName(c.getName());
                temp.setStatistics2(w.getStatus());
                temp.setWorkedId(w.getId());

                result.add(temp);

            } else {
                // няма → EMPTY
                Company temp = new Company();
                temp.setId(c.getId());
                temp.setName(c.getName());
                temp.setStatistics2(FilingStatusMore.EMPTY);
                temp.setWorkedId(null);

                result.add(temp);
            }
        }

        mav.addObject("companies", result);
        mav.addObject("selectedMonth", month);
        mav.addObject("selectedYear", year);
        mav.addObject("search", search);
        mav.addObject("statuses", FilingStatusMore.values());

        return mav;
    }

    @PostMapping("/update-worked")
    public String updateWorked(@RequestParam Long id,
                               @RequestParam FilingStatusMore status,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {

        Company company = companyRepository.findById(id).orElseThrow();

        Optional<CompanyWorked> existing =
                companyWorkedRepository.findByCompanyAndMonthAndYear(company, month, year);

        CompanyWorked w;

        if (existing.isPresent()) {
            w = existing.get();
        } else {
            w = new CompanyWorked();
            w.setCompany(company);
            w.setMonth(month);
            w.setYear(year);
        }

        w.setStatus(status);

        companyWorkedRepository.save(w);

        return "redirect:/companies/worked?month=" + month + "&year=" + year;
    }

    @PostMapping("/copy-month")
    public String copyMonth(@RequestParam Integer month,
                            @RequestParam Integer year) {

        int nextMonth = month + 1;
        int nextYear = year;

        if (nextMonth > 12) {
            nextMonth = 1;
            nextYear++;
        }

        //  копираме нищо

        return "redirect:/companies/worked?month=" + nextMonth + "&year=" + nextYear;
    }

    // delete by id in Worker Company
    @PostMapping("/worked/delete/{id}")
    public String deleteWorked(
            @PathVariable Long id,
            @RequestParam Integer month,
            @RequestParam Integer year
    ) {
        companyWorkedService.deleteById(id);

        return "redirect:/companies?month=" + month + "&year=" + year;
    }
}

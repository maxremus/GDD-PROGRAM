package org.example.gp.controller;

import org.example.gp.dto.SalaryResult;
import org.example.gp.service.SalaryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/companies")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/net-to-gross")
    public String page() {
        return "net-to-gross";
    }

    @PostMapping("/net-to-gross")
    public String calculate(
            @RequestParam double grossSalary,
            Model model) {

        SalaryResult result =
                salaryService.calculate(grossSalary);

        model.addAttribute("result", result);

        return "net-to-gross";
    }
}

package org.example.gp.controller;

import org.example.gp.dto.ExperienceResult;
import org.example.gp.dto.DateRange;
import org.example.gp.service.WorkExperienceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/companies")
public class WorkExperienceController {

    private final WorkExperienceService service;

    public WorkExperienceController(WorkExperienceService service) {
        this.service = service;
    }

    @GetMapping("/experience")
    public String showForm() {
        return "experience";
    }

    @PostMapping("/experience")
    public String calculate(
            @RequestParam String startDate,
            @RequestParam String endDate,
            Model model) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Period period = service.calculateExperience(start, end);

        model.addAttribute("years", period.getYears());
        model.addAttribute("months", period.getMonths());
        model.addAttribute("days", period.getDays());

        return "experience";
    }

    @PostMapping("/experience-noi")
    public String calculateNOI(
            @RequestParam List<String> startDates,
            @RequestParam List<String> endDates,
            Model model) {

        List<DateRange> ranges = new ArrayList<>();

        for (int i = 0; i < startDates.size(); i++) {
            ranges.add(new DateRange(
                    LocalDate.parse(startDates.get(i)),
                    LocalDate.parse(endDates.get(i))
            ));
        }

        ExperienceResult result = service.calculateNOI(ranges);

        model.addAttribute("years", result.getYears());
        model.addAttribute("months", result.getMonths());
        model.addAttribute("days", result.getDays());

        return "experience";
    }
}

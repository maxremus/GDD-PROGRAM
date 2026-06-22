package org.example.gp.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.gp.service.VatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/companies")
public class VatController {

    private final VatService vatService;

    public VatController(VatService vatService) {
        this.vatService = vatService;
    }

    @GetMapping("/vat")
    public String showForm() {
        log.info("Отворен е ДДС калкулаторът");
        return "vat";
    }

    @PostMapping("/vat")
    public String calculateVat(
            @RequestParam double amount,
            @RequestParam double rate,
            Model model) {

        double vat = vatService.calculateVat(amount, rate);
        double total = vatService.calculateTotal(amount, rate);

        model.addAttribute("amount", String.format("%.2f", amount));
        model.addAttribute("vat", String.format("%.2f", vat));
        model.addAttribute("total", String.format("%.2f", total));

        return "vat";
    }
}

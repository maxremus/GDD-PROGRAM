package org.example.gp.controller;

import org.example.gp.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ForgotPasswordController {

    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String usernameOrEmail,
                               RedirectAttributes redirectAttributes) {
        passwordResetService.requestReset(usernameOrEmail);
        // Винаги едно и също съобщение — не разкриваме дали акаунтът съществува
        redirectAttributes.addFlashAttribute("successMessage",
                "Ако акаунтът съществува и има зададен имейл, изпратихме линк за нулиране на паролата.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        boolean valid = passwordResetService.isTokenValid(token);
        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Паролите не съвпадат.");
            return "redirect:/reset-password?token=" + token;
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Паролата трябва да е поне 6 символа.");
            return "redirect:/reset-password?token=" + token;
        }

        boolean success = passwordResetService.resetPassword(token, newPassword);
        if (!success) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Линкът е невалиден или изтекъл. Моля, заявете нов от 'Забравена парола'.");
            return "redirect:/reset-password?token=" + token;
        }

        return "redirect:/login?resetSuccess=true";
    }
}

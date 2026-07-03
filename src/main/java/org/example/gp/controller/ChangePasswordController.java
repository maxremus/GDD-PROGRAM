package org.example.gp.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChangePasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public ChangePasswordController(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/change-password")
    public String changePage() {
        return "change-password";
    }

    @PostMapping("/change-password")
    @Transactional
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {

        // Взимаме username на текущо логнатия потребител
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String loggedInUsername = auth.getName();

        // Зареждаме свеж запис директно от БД (не от кеш)
        User user = userRepository.findByUsername(loggedInUsername).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Потребителят не е намерен.");
            return "redirect:/change-password";
        }

        // Изчистваме Hibernate кеша за да сме сигурни че имаме актуалната парола
        entityManager.refresh(user);

        // Проверяваме текущата парола спрямо хеша на ЛОГНАТИЯ потребител
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Текущата парола е грешна. Въведете паролата с която влязохте в системата.");
            return "redirect:/change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Новите пароли не съвпадат.");
            return "redirect:/change-password";
        }

        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Паролата трябва да е поне 6 символа.");
            return "redirect:/change-password";
        }

        // Записваме новата хеширана парола
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage",
            "Паролата е сменена успешно! При следващ вход използвайте новата парола.");
        return "redirect:/change-password";
    }
}

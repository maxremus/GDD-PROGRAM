package org.example.gp.controller;

import org.example.gp.dto.RegisterDto;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // ПУБЛИЧНА РЕГИСТРАЦИЯ — нова кантора се регистрира сама
    // =========================================================================

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerDto", new RegisterDto());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterDto registerDto,
                           RedirectAttributes redirectAttributes) {
        try {
            userService.registerOffice(registerDto);
            redirectAttributes.addFlashAttribute("successMessage",
                "Регистрацията е успешна! Вече може да влезете в системата.");
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }

    // =========================================================================
    // УПРАВЛЕНИЕ НА СЛУЖИТЕЛИ — за ROLE_OFFICE и ROLE_USER на кантората
    // =========================================================================

    @GetMapping("/office/staff")
    public String staffPage(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getOfficeId() == null) {
            return "redirect:/companies";
        }

        Long officeId = currentUser.getOfficeId();
        List<User> staff = userService.getUsersByOffice(officeId);

        // Намираме ime на кантората от ROLE_OFFICE потребителя
        String officeName = staff.stream()
                .filter(u -> "ROLE_OFFICE".equals(u.getRole()) && u.getOfficeName() != null)
                .map(User::getOfficeName)
                .findFirst()
                .orElse("Кантора #" + officeId);

        model.addAttribute("staff", staff);
        model.addAttribute("officeName", officeName);
        model.addAttribute("isOwner", "ROLE_OFFICE".equals(currentUser.getRole()));
        return "office-staff";
    }

    @PostMapping("/office/staff/add")
    public String addStaff(@RequestParam String username,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getOfficeId() == null) {
            return "redirect:/companies";
        }
        // Само собственикът (ROLE_OFFICE) може да добавя служители
        if (!"ROLE_OFFICE".equals(currentUser.getRole())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Нямате права за тази операция.");
            return "redirect:/office/staff";
        }
        try {
            userService.addStaffToOffice(username, password, currentUser.getOfficeId());
            redirectAttributes.addFlashAttribute("successMessage",
                "Служителят '" + username + "' е добавен успешно.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/office/staff";
    }

    @PostMapping("/office/staff/delete/{id}")
    public String deleteStaff(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !"ROLE_OFFICE".equals(currentUser.getRole())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Нямате права за тази операция.");
            return "redirect:/office/staff";
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null
                || !target.getOfficeId().equals(currentUser.getOfficeId())
                || target.getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Не може да изтриете този потребител.");
            return "redirect:/office/staff";
        }
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("successMessage", "Служителят е премахнат.");
        return "redirect:/office/staff";
    }

    // =========================================================================
    // СИСТЕМЕН ADMIN — вижда всички кантори и потребители
    // =========================================================================

    @GetMapping("/admin/users")
    public String adminUsersPage(Model model) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return "redirect:/companies";
        }
        model.addAttribute("users", userService.getAllUsers());
        return "admin-users";
    }

    @PostMapping("/admin/users/delete/{id}")
    public String adminDeleteUser(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return "redirect:/companies";
        }
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("successMessage", "Потребителят е изтрит.");
        return "redirect:/admin/users";
    }

    // =========================================================================
    // Помощен метод
    // =========================================================================
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}

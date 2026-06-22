package org.example.gp.controller;

import com.stripe.exception.StripeException;
import org.example.gp.entity.PlanType;
import org.example.gp.entity.Subscription;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // GET /subscription — показва текущ план + trial статус + бутони за избор
    // -------------------------------------------------------------------------
    @GetMapping("/subscription")
    public String subscriptionPage(Model model) {
        User user = getCurrentUser();
        if (user == null || user.getOfficeId() == null) {
            return "redirect:/companies";
        }

        Subscription sub = subscriptionService.getByOfficeId(user.getOfficeId());
        long daysLeft = subscriptionService.daysLeftInTrial(user.getOfficeId());

        model.addAttribute("subscription", sub);
        model.addAttribute("daysLeft", daysLeft);
        model.addAttribute("plans", PlanType.values());

        return "subscription";
    }

    // -------------------------------------------------------------------------
    // POST /subscription/checkout — създава Stripe Checkout сесия и редиректва
    // -------------------------------------------------------------------------
    @PostMapping("/subscription/checkout")
    public String checkout(@RequestParam PlanType plan,
                           RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        if (user == null || user.getOfficeId() == null) {
            return "redirect:/companies";
        }

        try {
            String checkoutUrl = subscriptionService.createCheckoutSession(
                    user.getOfficeId(), plan, null);
            return "redirect:" + checkoutUrl;
        } catch (StripeException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Грешка при свързване с платежната система: " + e.getMessage());
            return "redirect:/subscription";
        }
    }

    // -------------------------------------------------------------------------
    // GET /subscription/success — Stripe пренасочва тук след успешно плащане
    // -------------------------------------------------------------------------
    @GetMapping("/subscription/success")
    public String success(@RequestParam(required = false) String session_id, Model model) {
        model.addAttribute("successMessage",
            "Плащането е успешно! Вашият абонамент е активиран.");
        return "subscription-success";
    }

    @GetMapping("/subscription/cancel")
    public String cancel(Model model) {
        model.addAttribute("errorMessage", "Плащането беше отказано.");
        return "subscription-cancel";
    }

    // -------------------------------------------------------------------------
    // Помощен метод
    // -------------------------------------------------------------------------
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}

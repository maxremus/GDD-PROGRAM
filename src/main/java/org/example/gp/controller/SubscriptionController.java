package org.example.gp.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
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

    // GET /subscription — страница с планове и текущ статус
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

    // GET /subscription/checkout — предпазва от директно отваряне в браузъра
    @GetMapping("/subscription/checkout")
    public String checkoutGet() {
        return "redirect:/subscription";
    }

    // POST /subscription/checkout — отваря Stripe Checkout
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
    // GET /subscription/success
    // Stripe пренасочва тук след успешно плащане.
    // ВАЖНО: Активираме абонамента директно от session_id — не чакаме webhook.
    // Webhook-ът ще дойде и ще потвърди допълнително, но потребителят
    // трябва да може да влезе ВЕДНАГА след плащане.
    // -------------------------------------------------------------------------
    @GetMapping("/subscription/success")
    public String success(@RequestParam(required = false) String session_id, Model model) {
        User user = getCurrentUser();

        if (session_id != null && user != null && user.getOfficeId() != null) {
            try {
                // Взимаме session от Stripe и активираме абонамента веднага
                Session session = Session.retrieve(session_id);
                if (session != null && "complete".equals(session.getStatus())) {
                    String officeIdMeta = session.getMetadata().get("officeId");
                    String planMeta     = session.getMetadata().get("plan");
                    String customerId   = session.getCustomer();
                    String subId        = session.getSubscription();

                    if (officeIdMeta != null && planMeta != null) {
                        subscriptionService.handleCheckoutCompleted(
                                officeIdMeta, planMeta, customerId, subId);
                    }
                }
            } catch (Exception e) {
                // Ако Stripe API не отговори — webhook ще активира по-късно
                e.printStackTrace();
            }
        }

        model.addAttribute("successMessage",
            "Плащането е успешно! Вашият абонамент е активиран.");
        return "subscription-success";
    }

    @GetMapping("/subscription/cancel")
    public String cancel(Model model) {
        model.addAttribute("errorMessage", "Плащането беше отказано. Може да опитате отново.");
        return "subscription-cancel";
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}

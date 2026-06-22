package org.example.gp.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.example.gp.entity.PlanType;
import org.example.gp.entity.Subscription;
import org.example.gp.entity.SubscriptionStatus;
import org.example.gp.repository.CompanyRepository;
import org.example.gp.repository.SubscriptionRepository;
import org.example.gp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SubscriptionService {

    private static final int TRIAL_DAYS = 14;

    private final SubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               CompanyRepository companyRepository,
                               UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Извиква се веднага след регистрация на нова кантора.
    // Стартира 14-дневен trial с BASIC план.
    // -------------------------------------------------------------------------
    public Subscription startTrial(Long officeId) {
        Subscription sub = Subscription.builder()
                .officeId(officeId)
                .plan(PlanType.BASIC)
                .status(SubscriptionStatus.TRIAL)
                .trialEndsAt(LocalDateTime.now().plusDays(TRIAL_DAYS))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return subscriptionRepository.save(sub);
    }

    public Subscription getByOfficeId(Long officeId) {
        return subscriptionRepository.findByOfficeId(officeId).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Проверява дали кантората има право на достъп в момента.
    // -------------------------------------------------------------------------
    public boolean hasAccess(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;

        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            return true;
        }
        if (sub.getStatus() == SubscriptionStatus.TRIAL) {
            return sub.getTrialEndsAt() != null && LocalDateTime.now().isBefore(sub.getTrialEndsAt());
        }
        return false; // PAST_DUE, CANCELED
    }

    public long daysLeftInTrial(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null || sub.getStatus() != SubscriptionStatus.TRIAL || sub.getTrialEndsAt() == null) {
            return 0;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), sub.getTrialEndsAt()).toDays();
        return Math.max(days, 0);
    }

    // -------------------------------------------------------------------------
    // Проверка на лимитите според плана (брой фирми / служители).
    // -------------------------------------------------------------------------
    public boolean canAddMoreCompanies(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;
        long currentCount = companyRepository.findByOfficeId(officeId).size();
        return currentCount < sub.getPlan().getMaxCompanies();
    }

    public boolean canAddMoreStaff(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;
        long currentCount = userRepository.findByOfficeId(officeId).size();
        return currentCount < sub.getPlan().getMaxStaff();
    }

    // -------------------------------------------------------------------------
    // Създава Stripe Checkout сесия за конкретен план.
    // -------------------------------------------------------------------------
    public String createCheckoutSession(Long officeId, PlanType plan, String customerEmail) throws StripeException {

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(baseUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/subscription/cancel")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(plan.getStripePriceId())
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("officeId", String.valueOf(officeId))
                .putMetadata("plan", plan.name());

        Subscription existing = getByOfficeId(officeId);
        if (existing != null && existing.getStripeCustomerId() != null) {
            paramsBuilder.setCustomer(existing.getStripeCustomerId());
        } else if (customerEmail != null) {
            paramsBuilder.setCustomerEmail(customerEmail);
        }

        Session session = Session.create(paramsBuilder.build());
        return session.getUrl();
    }

    // -------------------------------------------------------------------------
    // Webhook handlers
    // -------------------------------------------------------------------------

    public void handleCheckoutCompleted(String officeIdStr, String planStr,
                                        String stripeCustomerId, String stripeSubscriptionId) {
        Long officeId = Long.valueOf(officeIdStr);
        PlanType plan = PlanType.valueOf(planStr);

        Subscription sub = subscriptionRepository.findByOfficeId(officeId)
                .orElseGet(() -> Subscription.builder().officeId(officeId).createdAt(LocalDateTime.now()).build());

        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeCustomerId(stripeCustomerId);
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        sub.setUpdatedAt(LocalDateTime.now());

        subscriptionRepository.save(sub);
    }

    public void handleSubscriptionUpdated(String stripeSubscriptionId, String stripeStatus,
                                          LocalDateTime currentPeriodEnd) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            sub.setStatus(mapStripeStatus(stripeStatus));
            sub.setCurrentPeriodEnd(currentPeriodEnd);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
        });
    }

    public void handleSubscriptionDeleted(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELED);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
        });
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active", "trialing" -> SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid", "incomplete" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.PAST_DUE;
        };
    }
}

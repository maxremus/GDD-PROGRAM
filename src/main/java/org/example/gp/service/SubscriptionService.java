package org.example.gp.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.example.gp.config.SubscriptionConstants;
import org.example.gp.entity.PlanType;
import org.example.gp.entity.Subscription;
import org.example.gp.entity.SubscriptionStatus;
import org.example.gp.entity.User;
import org.example.gp.repository.CompanyRepository;
import org.example.gp.repository.SubscriptionRepository;
import org.example.gp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionService {

    private static final int TRIAL_DAYS = 14;

    private final SubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               CompanyRepository companyRepository,
                               UserRepository userRepository,
                               EmailService emailService) {
        this.subscriptionRepository = subscriptionRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Намира собственика (ROLE_OFFICE) на дадена кантора — за адресиране на известия. */
    private Optional<User> findOfficeOwner(Long officeId) {
        return userRepository.findByOfficeId(officeId).stream()
                .filter(u -> "ROLE_OFFICE".equals(u.getRole()))
                .findFirst();
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

    /**
     * Проверява дали кантората е освободена от абонамент (вечен достъп).
     * ДИАНА - КИРИЛОВИ ЕООД / ЕИК 1781774289123.
     */
    public boolean isExempt(Long officeId) {
        if (officeId == null) return false;
        if (SubscriptionConstants.EXEMPT_OFFICE_ID.equals(officeId)) return true;

        return userRepository.findByOfficeId(officeId).stream().anyMatch(user ->
                SubscriptionConstants.EXEMPT_EIK.equals(user.getOfficeEik())
                        || (user.getOfficeName() != null
                            && user.getOfficeName().trim().equalsIgnoreCase(
                                    SubscriptionConstants.EXEMPT_OFFICE_NAME)));
    }

    // -------------------------------------------------------------------------
    // Проверява дали кантората има право на достъп в момента.
    // -------------------------------------------------------------------------
    public boolean hasAccess(Long officeId) {
        if (isExempt(officeId)) return true;

        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;

        expireIfNeeded(sub);

        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            if (sub.getCurrentPeriodEnd() == null) return true;
            return LocalDateTime.now().isBefore(sub.getCurrentPeriodEnd());
        }
        if (sub.getStatus() == SubscriptionStatus.TRIAL) {
            return sub.getTrialEndsAt() != null && LocalDateTime.now().isBefore(sub.getTrialEndsAt());
        }
        return false; // PAST_DUE, CANCELED
    }

    /**
     * Актуализира статуса при изтекъл trial или платен период.
     * Извиква се от hasAccess() и от планирания scheduler.
     */
    public void expireIfNeeded(Subscription sub) {
        if (sub == null || isExempt(sub.getOfficeId())) return;

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        if (sub.getStatus() == SubscriptionStatus.TRIAL
                && sub.getTrialEndsAt() != null
                && !now.isBefore(sub.getTrialEndsAt())) {
            sub.setStatus(SubscriptionStatus.CANCELED);
            changed = true;
        }

        if (sub.getStatus() == SubscriptionStatus.ACTIVE
                && sub.getCurrentPeriodEnd() != null
                && !now.isBefore(sub.getCurrentPeriodEnd())) {
            sub.setStatus(SubscriptionStatus.CANCELED);
            changed = true;
        }

        if (changed) {
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
        }
    }

    /** Съобщение за UI според текущото състояние на абонамента. */
    public String getAccessDeniedMessage(Long officeId) {
        if (isExempt(officeId)) return null;

        Subscription sub = getByOfficeId(officeId);
        if (sub == null) {
            return "Нямате активен абонамент. Моля, изберете план за да продължите.";
        }

        expireIfNeeded(sub);

        if (sub.getStatus() == SubscriptionStatus.TRIAL) {
            return "Безплатният пробен период е изтекъл. Изберете план по-долу, за да продължите да ползвате системата.";
        }
        if (sub.getStatus() == SubscriptionStatus.PAST_DUE) {
            return "Плащането не е успешно. Моля, обновете абонамента си.";
        }
        if (sub.getStatus() == SubscriptionStatus.CANCELED) {
            if (sub.getTrialEndsAt() != null) {
                return "Пробният период приключи. Активирайте абонамент, за да възстановите достъпа.";
            }
            return "Абонаментът ви е изтекъл. Изберете план, за да възстановите достъпа до системата.";
        }
        return "Нямате активен абонамент. Моля, изберете план за да продължите.";
    }

    public long daysLeftInTrial(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null || sub.getStatus() != SubscriptionStatus.TRIAL || sub.getTrialEndsAt() == null) {
            return 0;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), sub.getTrialEndsAt()).toDays();
        return Math.max(days, 0);
    }

    public long daysLeftInPeriod(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null || sub.getStatus() != SubscriptionStatus.ACTIVE || sub.getCurrentPeriodEnd() == null) {
            return 0;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), sub.getCurrentPeriodEnd()).toDays();
        return Math.max(days, 0);
    }

    public boolean isTrialExpired(Long officeId) {
        Subscription sub = getByOfficeId(officeId);
        if (sub == null || sub.getStatus() != SubscriptionStatus.TRIAL) return false;
        return sub.getTrialEndsAt() != null && !LocalDateTime.now().isBefore(sub.getTrialEndsAt());
    }

    // -------------------------------------------------------------------------
    // Проверка на лимитите според плана (брой фирми / служители).
    // -------------------------------------------------------------------------
    public boolean canAddMoreCompanies(Long officeId) {
        if (isExempt(officeId)) return true;
        if (!hasAccess(officeId)) return false;

        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;
        long currentCount = companyRepository.findByOfficeId(officeId).size();
        return currentCount < sub.getPlan().getMaxCompanies();
    }

    public boolean canAddMoreStaff(Long officeId) {
        if (isExempt(officeId)) return true;
        if (!hasAccess(officeId)) return false;

        Subscription sub = getByOfficeId(officeId);
        if (sub == null) return false;
        long currentCount = userRepository.findByOfficeId(officeId).size();
        return currentCount < sub.getPlan().getMaxStaff();
    }

    // -------------------------------------------------------------------------
    // Създава Stripe Checkout сесия за конкретен план.
    // -------------------------------------------------------------------------
    public String createCheckoutSession(Long officeId, PlanType plan, String customerEmail) throws StripeException {

        validatePlanLimits(officeId, plan);

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
    validatePlanLimits(officeId, plan);

    Subscription sub = subscriptionRepository.findByOfficeId(officeId)
            .orElseGet(() -> Subscription.builder()
                    .officeId(officeId)
                    .createdAt(LocalDateTime.now())
                    .build());

    LocalDateTime now = LocalDateTime.now();

    sub.setPlan(plan);
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setStripeCustomerId(stripeCustomerId);
    sub.setStripeSubscriptionId(stripeSubscriptionId);

    // Ако има активен период - добавяме още 1 месец
    if (sub.getCurrentPeriodEnd() != null
            && sub.getCurrentPeriodEnd().isAfter(now)) {

        sub.setCurrentPeriodEnd(
                sub.getCurrentPeriodEnd().plusMonths(1)
        );

    } else {
        // Ако няма период или е изтекъл - започваме от днес
        sub.setCurrentPeriodEnd(now.plusMonths(1));
    }

    sub.setUpdatedAt(now);

    subscriptionRepository.save(sub);

    findOfficeOwner(officeId).ifPresent(owner ->
            emailService.send(
                    owner.getEmail(),
                    "Абонаментът е активиран",
                    "subscription-active",
                    Map.of(
                            "officeName",
                            owner.getOfficeName() != null
                                    ? owner.getOfficeName()
                                    : "вашата кантора",
                            "planName",
                            plan.name()
                    )
            )
    );
}

    public void handleSubscriptionUpdated(String stripeSubscriptionId, String stripeStatus,
                                          LocalDateTime currentPeriodEnd) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            if (isExempt(sub.getOfficeId())) return;

            sub.setStatus(mapStripeStatus(stripeStatus));
            sub.setCurrentPeriodEnd(currentPeriodEnd);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
        });
    }

    public void handleSubscriptionDeleted(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            if (isExempt(sub.getOfficeId())) return;

            sub.setStatus(SubscriptionStatus.CANCELED);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);

            findOfficeOwner(sub.getOfficeId()).ifPresent(owner ->
                    emailService.send(owner.getEmail(), "Абонаментът е прекратен", "subscription-canceled", Map.of(
                            "officeName", owner.getOfficeName() != null ? owner.getOfficeName() : "вашата кантора"
                    )));
        });
    }

    /** Планирана проверка — маркира изтекли абонаменти. */
    public void processExpiredSubscriptions() {
        subscriptionRepository.findAll().forEach(this::expireIfNeeded);
    }

    private void validatePlanLimits(Long officeId, PlanType plan) {
        long companies = companyRepository.findByOfficeId(officeId).size();
        if (companies > plan.getMaxCompanies()) {
            throw new IllegalStateException("Не можете да преминете към " + plan +
                    ". Имате " + companies + " фирми, а планът позволява максимум " +
                    plan.getMaxCompanies() + ".");
        }

        long staff = userRepository.findByOfficeId(officeId).size();
        if (staff > plan.getMaxStaff()) {
            throw new IllegalStateException("Не можете да преминете към " + plan +
                    ". Имате " + staff + " потребители, а планът позволява максимум " +
                    plan.getMaxStaff() + ".");
        }
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

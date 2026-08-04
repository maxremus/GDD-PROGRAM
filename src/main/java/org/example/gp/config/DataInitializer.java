package org.example.gp.config;

import org.example.gp.config.SubscriptionConstants;
import org.example.gp.entity.*;
import org.example.gp.repository.SubscriptionRepository;
import org.example.gp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionRepository subscriptionRepository;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public void run(String... args) {

        // --- Системен admin ---
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ROLE_ADMIN")
                    .officeId(null)
                    .officeName("Системен администратор")
                    .build();
            userRepository.save(admin);
            System.out.println("✅ Admin потребител създаден: admin / admin123");
        }

        // --- Безплатен вечен абонамент за ДИАНА - КИРИЛОВИ ЕООД (ЕИК 1781774289123) ---
        final Long DIANA_OFFICE_ID = SubscriptionConstants.EXEMPT_OFFICE_ID;

        userRepository.findByUsername("diana").ifPresentOrElse(diana -> {
            boolean updated = false;
            if (diana.getOfficeEik() == null || !SubscriptionConstants.EXEMPT_EIK.equals(diana.getOfficeEik())) {
                diana.setOfficeEik(SubscriptionConstants.EXEMPT_EIK);
                updated = true;
            }
            if (diana.getOfficeName() == null
                    || !SubscriptionConstants.EXEMPT_OFFICE_NAME.equalsIgnoreCase(diana.getOfficeName().trim())) {
                diana.setOfficeName(SubscriptionConstants.EXEMPT_OFFICE_NAME);
                updated = true;
            }
            if (updated) {
                userRepository.save(diana);
            }
        }, () -> {
            User diana = User.builder()
                    .username("diana")
                    .password(passwordEncoder.encode("diana123"))
                    .role("ROLE_OFFICE")
                    .officeId(DIANA_OFFICE_ID)
                    .officeName(SubscriptionConstants.EXEMPT_OFFICE_NAME)
                    .officeEik(SubscriptionConstants.EXEMPT_EIK)
                    .build();
            userRepository.save(diana);
            System.out.println("✅ Потребител ДИАНА - КИРИЛОВИ ЕООД създаден: diana / diana123");
        });

        // Вечен ACTIVE абонамент — без Stripe, без изтичане
        if (subscriptionRepository.findByOfficeId(DIANA_OFFICE_ID).isEmpty()) {
            Subscription freeSub = Subscription.builder()
                    .officeId(DIANA_OFFICE_ID)
                    .plan(PlanType.PRO)               // Pro план — 50 фирми, 10 служители
                    .status(SubscriptionStatus.ACTIVE)
                    .trialEndsAt(null)
                    .currentPeriodEnd(LocalDateTime.now().plusYears(100)) // 100 години напред
                    .stripeCustomerId(null)
                    .stripeSubscriptionId(null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            subscriptionRepository.save(freeSub);
            System.out.println("✅ Вечен безплатен Pro абонамент за ДИАНА - КИРИЛОВИ ЕООД активиран.");
        }

        System.out.println("=====================================================");
        System.out.println("  🚀 GDD стартиран успешно на Render");
        System.out.println("=====================================================");
    }
}

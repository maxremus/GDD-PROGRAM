package org.example.gp.config;

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

        // --- Безплатен вечен абонамент за Диана Кирилови ЕООД ---
        // officeId = 1000000001L — фиксиран ID за тази кантора
        final Long DIANA_OFFICE_ID = 1000000001L;

        if (userRepository.findByUsername("diana").isEmpty()) {
            User diana = User.builder()
                    .username("diana")
                    .password(passwordEncoder.encode("diana123"))
                    .role("ROLE_OFFICE")
                    .officeId(DIANA_OFFICE_ID)
                    .officeName("Диана Кирилови ЕООД")
                    .build();
            userRepository.save(diana);
            System.out.println("✅ Потребител Диана Кирилови ЕООД създаден: diana / diana123");
        }

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
            System.out.println("✅ Вечен безплатен Pro абонамент за Диана Кирилови ЕООД активиран.");
        }

        System.out.println("=====================================================");
        System.out.println("  🚀 GDD стартиран успешно на Render");
        System.out.println("=====================================================");
    }
}

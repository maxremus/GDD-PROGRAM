package org.example.gp.config;

import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ROLE_ADMIN")
                    .officeId(null)
                    .officeName("Системен администратор")
                    .build();
            userRepository.save(admin);
            System.out.println("=====================================================");
            System.out.println("  ✅  Системен admin създаден автоматично!");
            System.out.println("  👤  Username : admin");
            System.out.println("  🔑  Password : admin123");
            System.out.println("  ⚠️   Сменете паролата след първи вход!");
            System.out.println("=====================================================");
        }
    }
}

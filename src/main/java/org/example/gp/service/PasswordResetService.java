package org.example.gp.service;

import org.example.gp.entity.PasswordResetToken;
import org.example.gp.entity.User;
import org.example.gp.repository.PasswordResetTokenRepository;
import org.example.gp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final int TOKEN_VALID_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Стартира процес по нулиране на парола за потребител, идентифициран по
     * потребителско ime ИЛИ имейл. Винаги "успява" тихо (не разкрива дали
     * акаунтът съществува) — реалното изпращане на имейл става само ако е
     * намерен потребител с валиден имейл.
     */
    public void requestReset(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return;
        }
        String query = usernameOrEmail.trim();

        Optional<User> userOpt = userRepository.findByUsername(query);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(query);
        }
        if (userOpt.isEmpty() || userOpt.get().getEmail() == null || userOpt.get().getEmail().isBlank()) {
            return; // тихо — не разкриваме дали акаунтът съществува
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(resetToken);

        String resetLink = baseUrl + "/reset-password?token=" + token;

        emailService.send(user.getEmail(), "Нулиране на парола — GDD Program", "password-reset", Map.of(
                "username", user.getUsername(),
                "resetLink", resetLink,
                "validHours", TOKEN_VALID_HOURS
        ));
    }

    /** Проверява дали токенът е валиден (съществува, не е използван, не е изтекъл). */
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    /**
     * Прилага новата парола за токена, ако е валиден.
     * @return true при успех, false ако токенът е невалиден/изтекъл/използван
     */
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()));

        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();
        Optional<User> userOpt = userRepository.findById(resetToken.getUserId());
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        emailService.send(user.getEmail(), "Паролата ви беше сменена", "password-changed", Map.of(
                "username", user.getUsername(),
                "timestamp", LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        ));

        return true;
    }
}

package org.example.gp.service;

import org.example.gp.dto.RegisterDto;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Регистрира НОВА КАНТОРА.
     * Създава потребител с ROLE_OFFICE, генерира уникален officeId
     * и стартира 14-дневен безплатен trial.
     */
    public void registerOffice(RegisterDto dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException(
                "Потребителското ime '" + dto.getUsername() + "' вече е заето.");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Паролите не съвпадат.");
        }
        if (dto.getOfficeName() == null || dto.getOfficeName().isBlank()) {
            throw new IllegalArgumentException("Моля въведете ime на кантората.");
        }

        // officeId = timestamp — уникален за всяка нова кантора
        long officeId = System.currentTimeMillis();

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .officeId(officeId)
                .officeName(dto.getOfficeName())
                .role("ROLE_OFFICE")
                .build();

        userRepository.save(user);

        // Стартираме 14-дневен безплатен trial автоматично
        subscriptionService.startTrial(officeId);
    }

    /**
     * Добавя СЛУЖИТЕЛ към кантора.
     * Служителят получава същия officeId като кантората.
     * Проверява лимита за брой служители според плана.
     */
    public void addStaffToOffice(String username, String password, Long officeId) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                "Потребителското ime '" + username + "' вече е заето.");
        }

        if (!subscriptionService.canAddMoreStaff(officeId)) {
            throw new IllegalArgumentException(
                "Достигнат е лимита на служители за вашия план. Преминете към по-голям план.");
        }

        User staff = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .officeId(officeId)
                .officeName(null)
                .role("ROLE_USER")
                .build();

        userRepository.save(staff);
    }

    /** Всички потребители — само за системния ADMIN */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /** Всички служители на дадена кантора */
    public List<User> getUsersByOffice(Long officeId) {
        return userRepository.findByOfficeId(officeId);
    }

    /** Изтрива потребител */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /** Смяна на роля (ROLE_USER ↔ ROLE_OFFICE) в рамките на кантората */
    public void updateUserRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Потребителят не е намерен."));
        user.setRole(role);
        userRepository.save(user);
    }
}

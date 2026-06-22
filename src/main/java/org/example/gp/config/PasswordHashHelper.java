package org.example.gp.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * САМО ЗА РАЗРАБОТКА — генерира BCrypt хеш за парола.
 * Достъп: http://localhost:8080/dev/hash?password=вашапарола
 *
 * ВАЖНО: Изтрийте или закоментирайте класа преди production!
 * (Анотацията @Profile("dev") го изключва автоматично ако не сте в dev профил)
 */
@Profile("dev")
@RestController
public class PasswordHashHelper {

    @GetMapping("/dev/hash")
    public String hashPassword(@RequestParam String password) {
        return new BCryptPasswordEncoder().encode(password);
    }
}

package org.example.gp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SubscriptionAccessFilter subscriptionAccessFilter;

    public SecurityConfig(SubscriptionAccessFilter subscriptionAccessFilter) {
        this.subscriptionAccessFilter = subscriptionAccessFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                // Публични ресурси
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                // Заглавна страница — публична
                .requestMatchers("/").permitAll()
                // Страница за регистрация на нова кантора — публична
                .requestMatchers("/register").permitAll()
                // Stripe webhook — публичен (проверка през подпис, не през login)
                .requestMatchers("/stripe/webhook").permitAll()
                // Системен admin панел
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Управление на служители — само собственик на кантора
                .requestMatchers("/office/**").hasAnyRole("OFFICE", "ADMIN")
                // Actuator
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Абонамент — достъпен за всеки логнат потребител на кантора
                .requestMatchers("/subscription/**").authenticated()
                // Всичко останало изисква вход
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/companies", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // Stripe webhook праща raw JSON без CSRF token — изключваме за този path
            .csrf(csrf -> csrf.ignoringRequestMatchers("/stripe/webhook"))
            // Проверка на абонамент — след автентикация
            .addFilterAfter(subscriptionAccessFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

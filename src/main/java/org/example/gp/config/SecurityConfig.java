package org.example.gp.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
                // ERROR dispatching — ТРЯБВА да е първо и да покрива и FORWARD/ERROR типове
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // Публични статични ресурси
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Заглавна страница — публична
                .requestMatchers("/").permitAll()
                // Регистрация — публична
                .requestMatchers("/register").permitAll()
                // Stripe webhook — публичен (сигурност чрез подпис)
                .requestMatchers("/stripe/webhook").permitAll()
                // Системен admin панел
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Управление на служители
                .requestMatchers("/office/**").hasAnyRole("OFFICE", "ADMIN")
                // Actuator — само ADMIN
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Абонамент — логнати потребители
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
            .csrf(csrf -> csrf.ignoringRequestMatchers("/stripe/webhook"))
            .addFilterAfter(subscriptionAccessFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

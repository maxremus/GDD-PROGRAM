package org.example.gp.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

    /**
     * Отделен filter chain с ВИСОК приоритет (@Order(1)) само за ERROR dispatches.
     * Позволява на Tomcat да обработи грешките без Security намеса.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain errorFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(request ->
                request.getDispatcherType() == DispatcherType.ERROR ||
                request.getDispatcherType() == DispatcherType.ASYNC)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Основен filter chain за всички останали заявки.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/register").permitAll()
                .requestMatchers("/stripe/webhook").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/office/**").hasAnyRole("OFFICE", "ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/subscription/**").authenticated()
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

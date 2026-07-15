package org.example.gp.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.AuditLogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SubscriptionAccessFilter subscriptionAccessFilter;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public SecurityConfig(SubscriptionAccessFilter subscriptionAccessFilter,
                           AuditLogService auditLogService,
                           UserRepository userRepository) {
        this.subscriptionAccessFilter = subscriptionAccessFilter;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /**
     * Напълно изключва Security за статични ресурси и /error.
     * Тези пътища не минават през НИКАКЪВ Security filter —
     * затова не може да получат Access Denied или error loop.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers(
                        "/",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/robots.txt",
                        "/sitemap.xml",
                        "/webjars/**",
                        "/error",
                        "/error/**"
                );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/register").permitAll()
                .requestMatchers("/forgot-password", "/reset-password").permitAll()
                .requestMatchers("/stripe/webhook").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/office/**").hasAnyRole("OFFICE", "ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/subscription/**").authenticated()
                .requestMatchers("/change-password").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    User user = userRepository.findByUsername(authentication.getName()).orElse(null);
                    auditLogService.log(authentication.getName(),
                            user != null ? user.getOfficeId() : null,
                            user != null ? user.getRole() : null,
                            "auth.login", "POST", "/login", "-",
                            clientIp(request), true, null);
                    response.sendRedirect(request.getContextPath() + "/companies");
                })
                .failureHandler((request, response, exception) -> {
                    String attemptedUser = request.getParameter("username");
                    auditLogService.log(attemptedUser, null, null,
                            "auth.login", "POST", "/login", "-",
                            clientIp(request), false, exception.getMessage());
                    response.sendRedirect(request.getContextPath() + "/login?error=true");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    if (authentication != null) {
                        auditLogService.log(authentication.getName(), null, null,
                                "auth.logout", "POST", "/logout", "-",
                                clientIp(request), true, null);
                    }
                    response.sendRedirect(request.getContextPath() + "/login?logout=true");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                // Неавтентикиран потребител опитва защитен ресурс (изтекла сесия, директен линк и т.н.)
                .authenticationEntryPoint((request, response, authException) ->
                        response.sendRedirect(request.getContextPath() + "/login?expired=true"))
                // 403 Forbidden — или изтекла сесия/CSRF токен (анонимен), или реално недостатъчни права (логнат)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean isAuthenticated = auth != null && auth.isAuthenticated()
                            && !(auth instanceof AnonymousAuthenticationToken);
                    if (isAuthenticated) {
                        response.sendRedirect(request.getContextPath() + "/companies?accessDenied=true");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login?expired=true");
                    }
                })
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

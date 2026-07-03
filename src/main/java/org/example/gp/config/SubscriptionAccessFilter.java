package org.example.gp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionAccessFilter(UserRepository userRepository,
                                    SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Пропускаме публични пътища — без проверка на абонамент
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Вземаме текущата автентикация
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Анонимен потребител или нелогнат — пропускаме (Security ще го пренасочи към /login)
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Намираме потребителя
        User user = userRepository.findByUsername(auth.getName()).orElse(null);

        // ADMIN, потребители без кантора, или ненамерен потребител — пропускаме
        if (user == null || user.getOfficeId() == null
                || "ROLE_ADMIN".equals(user.getRole())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Проверяваме дали кантората има активен абонамент
        if (!subscriptionService.hasAccess(user.getOfficeId())) {
            if (!response.isCommitted()) {
                response.sendRedirect("/subscription?expired=true");
            }
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Публични пътища — не изискват абонамент.
     * Забележка: /, /css/**, /images/**, /error вече са в
     * WebSecurityCustomizer.ignoring() и не достигат до този filter изобщо.
     * Тук добавяме останалите за допълнителна сигурност.
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/login")
                || path.startsWith("/logout")
                || path.startsWith("/register")
                || path.startsWith("/subscription")
                || path.startsWith("/stripe/webhook")
                || path.startsWith("/change-password")
                || path.startsWith("/actuator")
                || path.startsWith("/error")
                || path.startsWith("/css")
                || path.startsWith("/js")
                || path.startsWith("/images")
                || path.startsWith("/favicon")
                || path.startsWith("/webjars")
                || path.equals("/");
    }
}

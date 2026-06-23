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

/**
 * Проверява дали кантората има активен достъп.
 * Ако trial-ът е изтекъл → пренасочва към /subscription.
 */
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

        // Пропускаме всички публични и абонаментни пътища
        if (isAllowed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findByUsername(auth.getName()).orElse(null);

        // ADMIN не се проверява — системен потребител
        if (user == null || user.getOfficeId() == null || "ROLE_ADMIN".equals(user.getRole())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Проверяваме достъп само за кантори
        if (!subscriptionService.hasAccess(user.getOfficeId())) {
            response.sendRedirect("/subscription?expired=true");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String path) {
        return path.equals("/")
                || path.startsWith("/login")
                || path.startsWith("/logout")
                || path.startsWith("/register")
                || path.startsWith("/subscription")   // покрива /subscription, /subscription/checkout, /subscription/success, /subscription/cancel
                || path.startsWith("/stripe/webhook")
                || path.startsWith("/css")
                || path.startsWith("/js")
                || path.startsWith("/images")
                || path.startsWith("/actuator")
                || path.startsWith("/error");
    }
}

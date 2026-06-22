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
import java.util.Set;

/**
 * Проверява дали кантората (ROLE_OFFICE / ROLE_USER) има активен достъп
 * (trial или платен абонамент). Ако не — пренасочва към /subscription.
 *
 * ROLE_ADMIN е изключен от проверката (системен потребител).
 */
@Component
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/login", "/logout", "/register", "/subscription",
            "/subscription/checkout", "/subscription/success", "/subscription/cancel",
            "/stripe/webhook", "/css", "/js", "/images"
    );

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

        boolean isAllowed = ALLOWED_PATHS.stream().anyMatch(path::startsWith);
        if (isAllowed) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {

            User user = userRepository.findByUsername(auth.getName()).orElse(null);

            if (user != null && user.getOfficeId() != null && !"ROLE_ADMIN".equals(user.getRole())) {
                boolean hasAccess = subscriptionService.hasAccess(user.getOfficeId());
                if (!hasAccess) {
                    response.sendRedirect("/subscription?expired=true");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

package org.example.gp.config;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.gp.entity.User;
import org.example.gp.repository.UserRepository;
import org.example.gp.service.AuditLogService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Enumeration;

/**
 * Прихваща всяко действие (POST/PUT/DELETE/PATCH) в контролерите и го записва
 * в одит лога — кой, кога, какво, от къде, успешно ли е било.
 * Не са необходими промени по отделните контролери.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public AuditAspect(AuditLogService auditLogService, UserRepository userRepository) {
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Around("within(org.example.gp.controller..*) && " +
            "(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            " @annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            " @annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            " @annotation(org.springframework.web.bind.annotation.PatchMapping))")
    public Object auditControllerAction(ProceedingJoinPoint pjp) throws Throwable {
        String username = "anonymous";
        Long officeId = null;
        String role = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                officeId = user.getOfficeId();
                role = user.getRole();
            }
        }

        String action = buildActionName(pjp);
        String httpMethod = resolveHttpMethod(pjp);
        String requestUri = "-";
        String ipAddress = "-";
        String details = "-";

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            requestUri = request.getRequestURI();
            ipAddress = extractClientIp(request);
            details = buildDetails(request);
        }

        try {
            Object result = pjp.proceed();
            auditLogService.log(username, officeId, role, action, httpMethod, requestUri,
                    details, ipAddress, true, null);
            return result;
        } catch (Throwable ex) {
            auditLogService.log(username, officeId, role, action, httpMethod, requestUri,
                    details, ipAddress, false, ex.getMessage());
            throw ex;
        }
    }

    private String buildActionName(ProceedingJoinPoint pjp) {
        String className = pjp.getSignature().getDeclaringType().getSimpleName()
                .replace("Controller", "");
        String methodName = pjp.getSignature().getName();
        return className.toLowerCase() + "." + methodName;
    }

    private String resolveHttpMethod(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)) return "PATCH";
        return "-";
    }

    /** Параметрите на заявката, без пароли/чувствителни полета. */
    private String buildDetails(HttpServletRequest request) {
        Enumeration<String> names = request.getParameterNames();
        StringBuilder sb = new StringBuilder();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase();
            if (lower.contains("password") || lower.contains("token") || lower.contains("secret")) {
                continue;
            }
            String[] values = request.getParameterValues(name);
            String value = values != null ? String.join(",", values) : "";
            if (sb.length() > 0) sb.append(", ");
            sb.append(name).append("=").append(value);
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

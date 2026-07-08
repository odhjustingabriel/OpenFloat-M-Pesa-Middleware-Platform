package com.openfloat.mpesa.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final HttpServletRequest request;

    @Around("@annotation(audit)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        String username = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            username = auth.getName();
        }

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        String resource = audit.resource();
        String action = audit.action().name();

        log.debug("AOP Audit Interceptor matching action: {}, user: {}", action, username);

        Object result;
        try {
            result = joinPoint.proceed();
            
            // Log successful completion
            auditService.log(
                    username,
                    audit.action(),
                    resource,
                    null,
                    "Execution successful",
                    ipAddress
            );
            return result;
        } catch (Throwable t) {
            // Log failed execution
            auditService.log(
                    username,
                    audit.action(),
                    resource,
                    null,
                    "Execution failed: " + t.getMessage(),
                    ipAddress
            );
            throw t;
        }
    }
}

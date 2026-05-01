package pkqb.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pkqb.annotation.AuditLog;
import pkqb.service.AuditLogService;

import java.lang.reflect.Method;

/**
 * 审计日志切面
 * 拦截带有@AuditLog注解的方法，记录操作日志
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(pkqb.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        
        HttpServletRequest request = getRequest();
        Long userId = request != null ? (Long) request.getAttribute("userId") : null;
        String username = request != null ? (String) request.getAttribute("username") : null;
        String ipAddress = request != null ? getClientIp(request) : null;
        
        Object result = point.proceed();
        
        String detail = auditLog.detail();
        if (detail.isEmpty()) {
            detail = method.getName();
        }
        
        auditLogService.log(userId, username, auditLog.action(), auditLog.resource(), null, detail, ipAddress);
        
        return result;
    }
    
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

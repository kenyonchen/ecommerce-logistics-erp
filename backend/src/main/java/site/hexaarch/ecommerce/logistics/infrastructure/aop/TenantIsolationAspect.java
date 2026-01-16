package site.hexaarch.ecommerce.logistics.infrastructure.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

/**
 * 租户隔离AOP切面
 */
@Aspect
@Component
public class TenantIsolationAspect {

    /**
     * 定义切点：所有Repository方法
     */
    @Pointcut("execution(* site.hexaarch.ecommerce.logistics.domain..*Repository.*(..))")
    public void repositoryMethods() {
    }

    /**
     * 定义切点：所有服务方法
     */
    @Pointcut("execution(* site.hexaarch.ecommerce.logistics.application.service..*(..))")
    public void serviceMethods() {
    }

    /**
     * 在Repository方法执行前添加租户过滤
     */
    @Before("repositoryMethods()")
    public void beforeRepositoryMethod(JoinPoint joinPoint) {
        // 检查是否有租户ID
        if (TenantContext.hasTenantId()) {
            String tenantId = TenantContext.getTenantId();
            // 这里可以实现租户过滤逻辑
            // 例如，向方法参数中添加租户ID
            System.out.println("Tenant ID: " + tenantId + " for method: " + joinPoint.getSignature().getName());
        }
    }

    /**
     * 在服务方法执行前添加租户验证
     */
    @Before("serviceMethods()")
    public void beforeServiceMethod(JoinPoint joinPoint) {
        // 检查是否有租户ID
        if (!TenantContext.hasTenantId()) {
            // 对于需要租户ID的服务方法，可以抛出异常
            // throw new IllegalStateException("Tenant ID is required");
        }
    }
}
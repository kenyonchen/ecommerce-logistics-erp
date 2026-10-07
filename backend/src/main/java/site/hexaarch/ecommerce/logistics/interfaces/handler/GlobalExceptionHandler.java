package site.hexaarch.ecommerce.logistics.interfaces.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.InvalidOperationException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器，统一处理系统中的所有异常
 */
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * @param ex 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException ex) {
        logger.error("Business exception occurred: {}", ex.getMessage(), ex);

        Result<?> result = Result.error(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理实体未找到异常
     *
     * @param ex 实体未找到异常
     * @return 错误响应
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Result<?>> handleEntityNotFoundException(EntityNotFoundException ex) {
        logger.error("Entity not found exception occurred: {}", ex.getMessage(), ex);

        Result<?> result = Result.error(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(result);
    }

    /**
     * 处理无效操作异常
     *
     * @param ex 无效操作异常
     * @return 错误响应
     */
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Result<?>> handleInvalidOperationException(InvalidOperationException ex) {
        logger.error("Invalid operation exception occurred: {}", ex.getMessage(), ex);

        Result<?> result = Result.error(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理验证异常
     *
     * @param ex 验证异常
     * @return 错误响应
     */
    @ExceptionHandler(value = {ValidationException.class, BadCredentialsException.class})
    public ResponseEntity<Result<?>> handleValidationException(Exception ex) {
        //logger.error("Validation exception occurred: {}", ex.getMessage(), ex);
        Result<?> result = Result.fail(ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理领域状态守卫异常
     * <p>
     * 聚合内的状态机校验（如「物流单已处于终态不允许回退」）以 IllegalArgumentException /
     * IllegalStateException 抛出，属于调用方可修正的请求问题，若落到兜底 handler 会变成 500 且丢失原因。
     * </p>
     *
     * @param ex 状态守卫异常
     * @return 错误响应
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Result<?>> handleDomainGuardException(RuntimeException ex) {
        logger.warn("Domain guard exception occurred: {}", ex.getMessage());

        Result<?> result = Result.error("INVALID_OPERATION", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理方法参数验证异常
     *
     * @param ex 方法参数验证异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        logger.error("Method argument not valid exception occurred", ex);

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Result<?> result = Result.error("VALIDATION_ERROR", "请求参数验证失败", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理方法级鉴权失败
     * <p>
     * {@code @PreAuthorize} 抛出的 AccessDeniedException 会先经过 ControllerAdvice，落到兜底
     * handler 就变成 500 + SYSTEM_ERROR，前端只能提示"系统内部错误"、也无法区分"没登录"与"没权限"。
     * </p>
     *
     * @param ex 权限不足异常
     * @return 错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());

        Result<?> result = Result.error("ACCESS_DENIED", "没有权限执行该操作");
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(result);
    }

    /**
     * 处理请求绑定失败
     * <p>
     * 请求体无法反序列化（缺必填字段、枚举写法错）、query/path 参数类型不匹配（如
     * {@code status=DELIVERED} 不是 {@code OrderStatus} 的成员）、必填 query 参数缺失都发生在
     * 参数解析阶段，属于调用方可自行修正的 400；落到兜底 handler 会变成 500，
     * 只给前端一句"系统内部错误"，真实原因留在日志里。响应不回显原始异常消息，避免泄露值对象内部结构。
     * </p>
     *
     * @param ex 绑定异常
     * @return 错误响应
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Result<?>> handleRequestBindingException(Exception ex) {
        logger.warn("Request binding failed: {}", ex.getMessage());

        String parameterName = null;
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            parameterName = mismatch.getName();
        } else if (ex instanceof MissingServletRequestParameterException missing) {
            parameterName = missing.getParameterName();
        }
        Result<?> result = Result.error("VALIDATION_ERROR",
                parameterName == null
                        ? "请求体格式不正确，请检查必填字段与取值"
                        : "缺少必填参数 " + parameterName + " 或参数取值不合法");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理系统异常
     *
     * @param ex 系统异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleSystemException(Exception ex) {
        logger.error("System exception occurred: {}", ex.getMessage(), ex);

        return Result.error("SYSTEM_ERROR", "系统内部错误，请稍后重试");
    }
}

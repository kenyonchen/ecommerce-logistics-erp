package site.hexaarch.ecommerce.logistics.interfaces.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<?>> handleValidationException(ValidationException ex) {
        //logger.error("Validation exception occurred: {}", ex.getMessage(), ex);
        Result<?> result = Result.fail(ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    /**
     * 处理登录凭据错误
     * <p>
     * 与参数校验同为调用方可修正的问题，但语义是"身份未建立"，按 HTTP 规范应回 401；
     * 回 400 会让前端的"凭据错误"与"请求体不合法"共用一条分支。
     * </p>
     *
     * @param ex 凭据异常
     * @return 错误响应
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<?>> handleBadCredentialsException(BadCredentialsException ex) {
        logger.warn("Login failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Result.error("INVALID_CREDENTIALS", "用户名或密码错误"));
    }

    /**
     * 处理账号或所属租户被停用/锁定的登录拒绝。
     * <p>
     * 这两类异常过去没有专用出口，会落到兜底 handler 变成 500 SYSTEM_ERROR——
     * 用户看到的"系统故障"其实是"你的租户已被停用"，运维也会按故障去查。
     * 租户维度之所以归到"账号被停用"，是因为 {@code users.status} 完好、
     * 挡住它的是租户状态，文案里一并说明。
     * </p>
     *
     * @param ex 认证异常（停用或锁定）
     * @return 401 错误响应
     */
    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<Result<?>> handleAccountNotUsable(AuthenticationException ex) {
        logger.warn("Login rejected by account state: {}", ex.getMessage());
        boolean locked = ex instanceof LockedException;
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(locked ? "ACCOUNT_LOCKED" : "ACCOUNT_DISABLED",
                        locked ? "账号已被锁定，请稍后重试或联系平台管理员"
                                : "账号或其所属租户已被停用，请联系平台管理员"));
    }

    /**
     * 处理路由与方法不匹配
     * <p>
     * 未映射路径（NoResourceFoundException）与方法不支持（405）都发生在派发阶段，
     * 落到兜底 handler 会回 500 SYSTEM_ERROR，把调用方的拼错路径伪装成服务端崩溃，
     * 前端与监控都据此判故障。
     * </p>
     *
     * @param ex 路由异常
     * @return 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFoundException(NoResourceFoundException ex) {
        logger.warn("No handler for request: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Result.error("RESOURCE_NOT_FOUND", "请求的接口不存在"));
    }

    /**
     * 处理请求方法不支持（如该路径只有 POST 却用了 PUT）
     *
     * @param ex 方法不支持异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Unsupported method {} for {}", ex.getMethod(), ex.getSupportedHttpMethods());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error("METHOD_NOT_ALLOWED", "该路径不支持 " + ex.getMethod() + " 请求"));
    }

    /**
     * 处理 Content-Type 不支持（如multipart端点收到 application/json）
     *
     * @param ex 媒体类型异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        logger.warn("Unsupported Content-Type: {}", ex.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error("UNSUPPORTED_MEDIA_TYPE", "请求的 Content-Type 不被该接口支持"));
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
     * 处理唯一约束/外键等数据完整性冲突
     * <p>
     * 服务层的前置查重挡不住并发（两个请求同时通过校验后由 DB 的 UNIQUE 拦下），此前该类冲突
     * 落到兜底 handler 变成 500 + SYSTEM_ERROR，调用方无从知道是"重名/重复编码"还是"服务故障"，
     * 详见 docs/spec/warehouse.md 验收条件 1 与 ISS-116。冲突细节只写日志，响应不回显 SQL 与索引名。
     * </p>
     *
     * @param ex 数据完整性异常
     * @return 错误响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        Result<?> result = Result.error("DATA_CONFLICT", "数据冲突：该记录已存在，或仍被其他数据引用，无法完成本次操作");
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
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

package site.hexaarch.ecommerce.logistics.interfaces.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
        logger.error("Validation exception occurred: {}", ex.getMessage(), ex);

        Result<?> result = Result.error(ex.getErrorCode(), ex.getMessage());
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

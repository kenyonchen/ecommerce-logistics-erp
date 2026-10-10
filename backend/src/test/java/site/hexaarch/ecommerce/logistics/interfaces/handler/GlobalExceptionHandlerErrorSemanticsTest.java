package site.hexaarch.ecommerce.logistics.interfaces.handler;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 错误语义契约测试：HTTP 状态码必须与「调用方可自行修正」的程度一致，
 * 不允许把路由/凭据/媒体类型错误塌成 400 或 500。
 *
 * @author kenyon
 */
class GlobalExceptionHandlerErrorSemanticsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 登录凭据错误应回401而不是400() {
        var response = handler.handleBadCredentialsException(new BadCredentialsException("bad pwd"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().getCode());
    }

    @Test
    void 未映射路径应回404而不是500() {
        var response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "api/not-mapped"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getCode());
    }

    @Test
    void 请求方法不支持应回405() {
        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getCode());
    }

    @Test
    void 不支持的媒体类型应回415() {
        var response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("application/xml"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getCode());
    }

    @Test
    void 参数校验失败仍是400且不吞消息() {
        var response = handler.handleValidationException(new ValidationException("租户ID不能为空"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("租户ID不能为空", response.getBody().getMessage());
    }

    @Test
    void 唯一约束冲突应回409而不是500() {
        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry '上海仓' for key 'warehouses.UK7ix5']"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_CONFLICT", response.getBody().getCode());
    }

    @Test
    void 唯一约束冲突不回显SQL与索引名() {
        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry '上海仓' for key 'warehouses.UK7ix5']"));

        assertNotNull(response.getBody());
        assertFalse(response.getBody().getMessage().contains("Duplicate entry"));
        assertFalse(response.getBody().getMessage().contains("UK7ix5"));
    }
}

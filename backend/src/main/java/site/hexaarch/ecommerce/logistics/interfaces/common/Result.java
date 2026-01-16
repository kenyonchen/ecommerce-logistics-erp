package site.hexaarch.ecommerce.logistics.interfaces.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果类
 *
 * @param <T> 响应数据类型
 * @author kenyon
 */
@Data
@NoArgsConstructor
public class Result<T> {
    /**
     * 响应码
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 构造方法
     *
     * @param code    响应码
     * @param message 响应消息
     * @param data    响应数据
     */
    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应
     *
     * @param <T>  响应数据类型
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("200", "success", data);
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>("200", "success", null);
    }

    /**
     * 错误响应
     *
     * @param <T>     响应数据类型
     * @param code    错误码
     * @param message 错误消息
     * @return 错误响应
     */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 错误响应（兼容现有代码）
     *
     * @param <T>     响应数据类型
     * @param message 错误消息
     * @return 错误响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>("ERROR", message, null);
    }

    /**
     * 错误响应（带数据）
     *
     * @param <T>     响应数据类型
     * @param code    错误码
     * @param message 错误消息
     * @param data    错误数据
     * @return 错误响应
     */
    public static <T> Result<T> error(String code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /**
     * 失败响应（兼容现有代码）
     *
     * @param <T>     响应数据类型
     * @param message 错误消息
     * @return 错误响应
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>("ERROR", message, null);
    }
}

package com.pethome.common.exception;

import lombok.Getter;

/**
 * 自定义业务异常
 *
 * <p>code 为服务端业务错误码，仅用于日志定位；
 * 对外响应体遵循统一约定，失败时 {@code Result.code = 0}，提示信息取本异常的 message。</p>
 *
 * @author pet-home
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 默认业务错误码 */
    public static final int DEFAULT_CODE = 500;

    /** 业务错误码 */
    private final int code;

    public BusinessException(String message) {
        this(DEFAULT_CODE, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}

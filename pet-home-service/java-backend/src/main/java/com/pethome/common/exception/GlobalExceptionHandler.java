package com.pethome.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.pethome.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一转换为 {@link Result#error(String)} 格式，堆栈信息仅记录到日志，绝不返回给调用方。</p>
 *
 * @author pet-home
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 字段名 → 中文标签（用于把「请求体解析失败」提示得更可定位，可按需补充）
     */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "entryTime", "入职日期",
            "appointmentDate", "预约日期",
            "birthday", "生日",
            "date", "日期"
    );

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        // code 固定为 0（失败），业务错误码通过 bizCode 透出，便于前端按码处理
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常：@RequestBody + @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(msg);
    }

    /**
     * 参数校验异常：表单/普通对象绑定
     */
    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return Result.error(msg);
    }

    /**
     * 参数校验异常：方法级 @Validated 校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(msg);
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error("缺少必要参数：" + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName());
        return Result.error("参数类型不正确：" + e.getName());
    }

    /**
     * 请求体格式错误
     *
     * <p>JSON 语法错误统一提示「请求参数格式错误」；若某个字段的值类型不对（例如把
     * {@code yyyy-09-Tu} 传给 LocalDate 类型的入职日期），则尽量给出可定位的提示。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(describeParseError(e));
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式不支持: {}", e.getMessage());
        return Result.error("请求方式不支持：" + e.getMethod());
    }

    /**
     * 请求地址不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("请求地址不存在: {}", e.getResourcePath());
        return Result.error("请求地址不存在");
    }

    /**
     * 唯一索引冲突（数据库层兜底，例如分类名 uk_name、同分类服务名 uk_category_name）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return Result.error("数据已存在，请检查名称是否重复");
    }

    /**
     * 上传文件超过大小限制
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.error("上传文件过大，请压缩后重试");
    }

    /**
     * 兜底系统异常，不对外暴露堆栈细节
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    /**
     * 把「请求体解析失败」翻译成可定位的提示
     *
     * <p>例如：入职日期格式不正确，应为 yyyy-MM-dd。非字段类型问题时仍返回通用提示，
     * 避免把 Jackson 原始信息（含类名）暴露给调用方。</p>
     */
    private String describeParseError(HttpMessageNotReadableException e) {
        InvalidFormatException invalid = findInvalidFormatException(e);
        if (invalid == null) {
            return "请求参数格式错误";
        }

        String field = invalid.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
        String label = field == null ? "参数" : FIELD_LABELS.getOrDefault(field, "参数 " + field);
        String pattern = expectedPattern(invalid.getTargetType());
        return pattern == null ? label + " 类型不正确" : label + "格式不正确，应为 " + pattern;
    }

    /**
     * 在异常链中查找 Jackson 的字段值格式异常（最多向下 5 层，避免异常链异常时死循环）
     */
    private InvalidFormatException findInvalidFormatException(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 5; depth++) {
            if (current instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 目标类型对应的期望格式；非日期时间类型返回 null（改为提示「类型不正确」）
     */
    private String expectedPattern(Class<?> targetType) {
        if (targetType == null) {
            return null;
        }
        if (LocalDateTime.class.isAssignableFrom(targetType)) {
            return "yyyy-MM-dd HH:mm:ss";
        }
        if (LocalDate.class.isAssignableFrom(targetType)) {
            return "yyyy-MM-dd";
        }
        if (LocalTime.class.isAssignableFrom(targetType)) {
            return "HH:mm:ss";
        }
        return null;
    }

}

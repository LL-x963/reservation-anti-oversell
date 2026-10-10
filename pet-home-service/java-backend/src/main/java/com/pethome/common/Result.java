package com.pethome.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果
 *
 * <p>code 约定：1 表示成功，0 表示失败。</p>
 *
 * @author pet-home
 */
@Data
public class Result implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功 */
    public static final Integer SUCCESS_CODE = 1;
    /** 失败 */
    public static final Integer FAIL_CODE = 0;
    public static final String SUCCESS_MSG = "success";
    public static final String FAIL_MSG = "操作失败";

    /** 状态码：1 成功，0 失败 */
    private Integer code;
    /** 业务错误码，仅失败时返回（如 601 该时段不可约），成功时不返回 */
    private Integer bizCode;
    /** 提示信息 */
    private String msg;
    /** 响应数据 */
    private Object data;

    public Result() {
    }

    public Result(Integer code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Result(Integer code, Integer bizCode, String msg, Object data) {
        this.code = code;
        this.bizCode = bizCode;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功，无数据
     */
    public static Result success() {
        return new Result(SUCCESS_CODE, SUCCESS_MSG, null);
    }

    /**
     * 成功，携带数据
     */
    public static Result success(Object data) {
        return new Result(SUCCESS_CODE, SUCCESS_MSG, data);
    }

    /**
     * 成功，自定义提示信息并携带数据
     */
    public static Result success(String msg, Object data) {
        return new Result(SUCCESS_CODE, msg, data);
    }

    /**
     * 失败，默认提示信息
     */
    public static Result error() {
        return new Result(FAIL_CODE, FAIL_MSG, null);
    }

    /**
     * 失败，自定义提示信息
     */
    public static Result error(String msg) {
        return new Result(FAIL_CODE, msg, null);
    }

    /**
     * 失败，携带业务错误码与提示信息
     *
     * @param bizCode 业务错误码，见项目错误码约定（如 601/602/603）
     */
    public static Result error(Integer bizCode, String msg) {
        return new Result(FAIL_CODE, bizCode, msg, null);
    }

}

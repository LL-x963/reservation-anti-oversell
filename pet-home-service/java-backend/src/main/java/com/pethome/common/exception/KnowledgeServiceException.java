package com.pethome.common.exception;

/**
 * 知识库（RAG）服务调用异常
 *
 * <p>由 {@code GlobalExceptionHandler} 统一捕获，响应体遵循项目约定：
 * {@code code = 0}（失败）、{@code bizCode = 600}、提示信息固定为 {@link #MESSAGE}。</p>
 *
 * @author pet-home
 */
public class KnowledgeServiceException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    public static final int CODE = 600;

    /** 对外固定提示信息 */
    public static final String MESSAGE = "知识库服务暂时繁忙，请稍后再试；您也可以直接联系门店客服咨询。";

    public KnowledgeServiceException() {
        super(CODE, MESSAGE);
    }

    public KnowledgeServiceException(Throwable cause) {
        super(CODE, MESSAGE, cause);
    }

}

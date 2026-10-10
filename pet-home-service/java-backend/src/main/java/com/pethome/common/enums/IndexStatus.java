package com.pethome.common.enums;

/**
 * 知识库文档索引状态
 *
 * <p>0未索引 1索引中 2正常 3失败。</p>
 *
 * @author pet-home
 */
public enum IndexStatus {

    /** 未索引（新增或内容变更后待重建） */
    NOT_INDEXED(0, "未索引"),

    /** 索引中（重建索引进行中） */
    INDEXING(1, "索引中"),

    /** 正常（已成功建立索引） */
    NORMAL(2, "正常"),

    /** 失败（重建索引失败，可重试） */
    FAILED(3, "失败");

    /** 状态码 */
    private final Integer code;

    /** 状态描述 */
    private final String desc;

    IndexStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按状态码获取描述，未知状态码返回 null
     */
    public static String descOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (IndexStatus status : values()) {
            if (status.code.equals(code)) {
                return status.desc;
            }
        }
        return null;
    }

}

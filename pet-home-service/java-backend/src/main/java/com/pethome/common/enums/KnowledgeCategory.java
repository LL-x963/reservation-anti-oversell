package com.pethome.common.enums;

import com.pethome.common.exception.BusinessException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 知识库文档分类（固定三类）
 *
 * <p>数据库中按中文名称存储，仅允许：护理、饮食、疾病预防。</p>
 *
 * @author pet-home
 */
public enum KnowledgeCategory {

    /** 护理 */
    CARE("护理"),

    /** 饮食 */
    DIET("饮食"),

    /** 疾病预防 */
    DISEASE_PREVENTION("疾病预防");

    /** 分类名称校验正则（供 DTO 的 @Pattern 复用，避免两处硬编码） */
    public static final String PATTERN = "^(护理|饮食|疾病预防)$";

    /** 全部可选值，用于错误提示 */
    private static final String ALL_VALUES = Arrays.stream(values())
            .map(KnowledgeCategory::getValue)
            .collect(Collectors.joining("、"));

    /** 分类名称 */
    private final String value;

    KnowledgeCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 按分类名称匹配枚举，取值非法时抛出业务异常
     *
     * @param value 分类名称
     * @return 匹配到的枚举
     */
    public static KnowledgeCategory of(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("文档分类不能为空，可选值：" + ALL_VALUES);
        }
        for (KnowledgeCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new BusinessException("文档分类不合法，可选值：" + ALL_VALUES);
    }

    /**
     * 是否为合法的分类名称
     */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (KnowledgeCategory category : values()) {
            if (category.value.equals(value)) {
                return true;
            }
        }
        return false;
    }

}

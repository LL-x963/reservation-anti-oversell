package com.pethome.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 统计粒度枚举
 *
 * @author pet-home
 */
@Getter
public enum StatType {

    /** 按自然日 */
    DAY("day"),
    /** 按自然周（周一至周日） */
    WEEK("week"),
    /** 按自然月 */
    MONTH("month");

    private final String value;

    StatType(String value) {
        this.value = value;
    }

    public static StatType fromValue(String value) {
        if (value != null) {
            for (StatType type : values()) {
                if (type.value.equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("未知的统计粒度: " + value);
    }

    public static String allValuesAsText() {
        return String.join("、", Arrays.stream(values()).map(StatType::getValue).toArray(String[]::new));
    }

}

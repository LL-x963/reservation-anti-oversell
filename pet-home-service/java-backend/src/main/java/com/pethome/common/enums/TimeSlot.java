package com.pethome.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 固定预约时段枚举（2 小时/段）
 *
 * @author pet-home
 */
@Getter
public enum TimeSlot {

    SLOT_10_12("10:00-12:00"),
    SLOT_12_14("12:00-14:00"),
    SLOT_14_16("14:00-16:00"),
    SLOT_16_18("16:00-18:00"),
    SLOT_18_20("18:00-20:00");

    /** 时段字面值，与 beautician_schedule.time_slot 存储值一致 */
    private final String value;

    TimeSlot(String value) {
        this.value = value;
    }

    /**
     * 按字面值查找时段
     */
    public static Optional<TimeSlot> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(slot -> slot.value.equals(trimmed))
                .findFirst();
    }

    /**
     * 判断是否为合法时段
     */
    public static boolean isValid(String value) {
        return of(value).isPresent();
    }

    /**
     * 全部时段字面值（按时间先后顺序）
     */
    public static List<String> allValues() {
        return Arrays.stream(values())
                .map(TimeSlot::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 全部时段字面值拼接文本，用于错误提示
     */
    public static String allValuesAsText() {
        return String.join("、", allValues());
    }

}

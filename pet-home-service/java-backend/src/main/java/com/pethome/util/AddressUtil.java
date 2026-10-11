package com.pethome.util;

import com.pethome.entity.AddressBook;
import org.apache.commons.lang3.StringUtils;

/**
 * 地址工具
 *
 * @author pet-home
 */
public final class AddressUtil {

    private AddressUtil() {
    }

    /**
     * 拼接完整地址：省 + 市 + 区 + 详细地址（省市区为空时自动跳过）
     *
     * @param address 地址簿
     * @return 完整地址；全部为空时返回 null
     */
    public static String fullAddress(AddressBook address) {
        if (address == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        append(builder, address.getProvinceName());
        append(builder, address.getCityName());
        append(builder, address.getDistrictName());
        append(builder, address.getDetail());
        return builder.isEmpty() ? null : builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (StringUtils.isNotBlank(part)) {
            builder.append(part.trim());
        }
    }

}

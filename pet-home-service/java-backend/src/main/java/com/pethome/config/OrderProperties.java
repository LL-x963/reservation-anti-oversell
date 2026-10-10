package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单配置项，对应 application.yaml 中的 pethome.order
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.order")
public class OrderProperties {

    /** 待支付超时自动取消延迟（毫秒），默认 30 分钟 */
    private long cancelDelayMillis = 30 * 60 * 1000L;

}

package com.pethome.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 拓扑启动期声明
 *
 * <p>RabbitAdmin 默认在首次建立连接时声明交换机/队列/绑定，若首个业务消息早于声明完成，
 * 会出现 NO_ROUTE（消息无法路由）导致延迟消息丢失。这里在应用启动时主动声明一次，
 * 保证业务发送前拓扑已就绪；Broker 不可用时不影响应用启动。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqTopologyInitializer implements ApplicationRunner {

    private final RabbitAdmin rabbitAdmin;

    @Override
    public void run(ApplicationArguments args) {
        try {
            rabbitAdmin.initialize();
            log.info("RabbitMQ 拓扑初始化完成（交换机/队列/绑定已声明）");
        } catch (Exception e) {
            log.warn("RabbitMQ 拓扑初始化失败，将在首次连接时自动声明: {}", e.getMessage());
        }
    }

}

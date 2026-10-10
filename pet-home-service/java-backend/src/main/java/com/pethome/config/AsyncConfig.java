package com.pethome.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步任务配置
 *
 * <p>用于评价后异步重算美容师平均评分等场景，线程池沿用 Spring Boot 默认的 applicationTaskExecutor。</p>
 *
 * @author pet-home
 */
@Configuration
@EnableAsync
public class AsyncConfig {

}

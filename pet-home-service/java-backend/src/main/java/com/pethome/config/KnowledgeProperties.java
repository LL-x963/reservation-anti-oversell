package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 知识库（RAG）服务配置项，对应 application.yaml 中的 pethome.knowledge
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.knowledge")
public class KnowledgeProperties {

    /** RAG 服务地址（pet-home-service/rag-service） */
    private String baseUrl = "http://localhost:8000";

    /** 与 rag-service 的 RAG_API_KEY 保持一致；默认空，由 pethome.knowledge.api-key 或环境变量注入 */
    private String apiKey = "";

    /** 存放 API Key 的请求头名称 */
    private String apiKeyHeader = "X-API-Key";

    /** 连接超时，默认 3 秒 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 问答读取超时，默认 30 秒（本地 1.5B 模型推理通常需要 2-10 秒） */
    private Duration readTimeout = Duration.ofSeconds(30);

    /** 重建索引读取超时，默认 5 分钟（需向量化全部文档，明显长于问答） */
    private Duration reindexReadTimeout = Duration.ofMinutes(5);

    /** 失败重试次数（不含首次调用），默认重试 1 次 */
    private int maxRetries = 1;

}

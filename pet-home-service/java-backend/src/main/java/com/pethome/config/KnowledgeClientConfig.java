package com.pethome.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 知识库（RAG）服务客户端配置
 *
 * <p>问答与重建索引各用一个 {@link RestTemplate}：两者的合理读取超时差异很大
 * （问答秒级、重建索引分钟级），因此分别建 Bean 而不是共用一个。</p>
 *
 * @author pet-home
 */
@Configuration
@RequiredArgsConstructor
public class KnowledgeClientConfig {

    /**
     * 问答用 RestTemplate（连接超时 3 秒、读取超时 30 秒，均可配置）
     */
    @Bean("knowledgeRestTemplate")
    public RestTemplate knowledgeRestTemplate(RestTemplateBuilder builder, KnowledgeProperties properties) {
        return builder
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build();
    }

    /**
     * 重建索引用 RestTemplate（连接超时 3 秒、读取超时 5 分钟，均可配置）
     */
    @Bean("knowledgeReindexRestTemplate")
    public RestTemplate knowledgeReindexRestTemplate(RestTemplateBuilder builder, KnowledgeProperties properties) {
        return builder
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReindexReadTimeout())
                .build();
    }

}

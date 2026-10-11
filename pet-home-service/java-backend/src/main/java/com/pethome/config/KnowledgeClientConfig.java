package com.pethome.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 知识库（RAG）服务客户端配置
 *
 * <p>问答与重建索引各用一个 {@link RestTemplate}：两者的合理读取超时差异很大
 * （问答秒级、重建索引分钟级），因此分别建 Bean 而不是共用一个。</p>
 *
 * <p><b>为什么必须显式指定 {@link SimpleClientHttpRequestFactory}：</b>
 * Spring Boot 3.4+ 的 {@link RestTemplateBuilder} 在类路径没有 Apache HttpClient /
 * OkHttp 时默认选用 JdkClientHttpRequestFactory（JDK HttpClient）。实测该工厂在本项目
 * 场景下上送 POST 请求体时，服务端（uvicorn + FastAPI）会收到「空请求体」，RAG 侧因此
 * 返回 422「请求参数不合法：请求体不能为空」，表现为「重建索引失败」「AI 问答失败」。
 * 改用 JDK HttpURLConnection（SimpleClientHttpRequestFactory）后请求体正常上送。</p>
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
                .requestFactory(SimpleClientHttpRequestFactory::new)
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
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReindexReadTimeout())
                .build();
    }

}

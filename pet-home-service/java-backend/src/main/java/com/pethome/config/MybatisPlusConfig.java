package com.pethome.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>主键自增策略：application.yaml 中配置 {@code mybatis-plus.global-config.db-config.id-type=auto}，
 * 同时实体主键使用 {@code @TableId(type = IdType.AUTO)} 显式声明。</p>
 *
 * <p>公共字段自动填充见 {@link MyMetaObjectHandler}。</p>
 *
 * @author pet-home
 */
@Configuration
@MapperScan("com.pethome.mapper")
public class MybatisPlusConfig {

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数限制，防止恶意传入超大 size
        paginationInnerInterceptor.setMaxLimit(500L);
        // 超出最大页数后不返回空数据，而是回到首页
        paginationInnerInterceptor.setOverflow(false);

        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

}

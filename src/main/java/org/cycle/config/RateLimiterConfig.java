package org.cycle.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流配置类
 * 使用Guava的RateLimiter实现请求限流
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 用户查询限流
     * 每秒最多处理100个请求
     */
    @Bean
    public RateLimiter userQueryRateLimiter() {
        return RateLimiter.create(100);
    }

    /**
     * 通用接口限流
     * 每秒最多处理200个请求
     */
    @Bean
    public RateLimiter generalRateLimiter() {
        return RateLimiter.create(200);
    }
}

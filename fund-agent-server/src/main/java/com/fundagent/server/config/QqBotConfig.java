package com.fundagent.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qq-bot")
public class QqBotConfig {

    private boolean enabled = true;

    private String appId;

    private String appSecret;

    private String websocketUrl;

    private ThreadPool threadPool = new ThreadPool();

    @Data
    public static class ThreadPool {
        private int coreSize = 5;
        private int maxSize = 20;
        private int queueCapacity = 20;
    }
}

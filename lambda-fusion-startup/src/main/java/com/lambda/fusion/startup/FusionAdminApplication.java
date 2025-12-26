package com.lambda.fusion.startup;

import cn.hutool.extra.spring.SpringUtil;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.cloud.sse.listener.DefaultSseEventListener;
import com.lambda.fusion.core.annotation.LambdaFusionApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@LambdaFusionApplication
public class FusionAdminApplication implements ApplicationRunner {

    public static void main(String[] args) {
        SpringApplication.run(FusionAdminApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        SseEmitterManager bean = SpringUtil.getBean(SseEmitterManager.class);
        bean.addEventListener(new DefaultSseEventListener());
    }

    @Configuration
    public static class CorsConfig {
        @Bean
        public WebMvcConfigurer corsConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/sse/**") // 你的 SSE 路径
                            .allowedOriginPatterns("*") // 或指定 http://localhost:63342
                            .allowedMethods("GET")
                            .allowCredentials(false)
                            .maxAge(3600);
                }
            };
        }
    }
}

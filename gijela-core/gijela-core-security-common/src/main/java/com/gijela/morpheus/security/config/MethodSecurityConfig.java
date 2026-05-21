package com.gijela.morpheus.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Boot 4 / Spring Security 7 方法安全配置。
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(MethodSecurityConfig.class);

    public MethodSecurityConfig() {
        log.info("[security-common] 启用方法安全配置");
    }

    @Bean(name = "methodSecurityExpressionHandler")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        CustomMethodSecurityExpressionHandler handler = new CustomMethodSecurityExpressionHandler();
        LoggerFactory.getLogger(MethodSecurityConfig.class)
                .info("[security-common] 注册自定义 MethodSecurityExpressionHandler: {}", handler.getClass().getName());
        return handler;
    }
}

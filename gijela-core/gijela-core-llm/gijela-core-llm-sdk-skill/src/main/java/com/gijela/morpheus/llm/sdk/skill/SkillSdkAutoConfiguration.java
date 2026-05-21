package com.gijela.morpheus.llm.sdk.skill;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Skill SDK 自动装配。引入本 jar 后自动暴露：
 * <ul>
 *   <li>{@link SkillSdkProperties} —— 绑定 {@code gijela.llm.skill.*}</li>
 *   <li>{@link SkillRegistry} —— 应用内全局单例（业务侧可通过 {@code register()} 注入 builtin/local）</li>
 * </ul>
 *
 * <p>注意：本模块零内置 Provider 自动注册，业务侧按需 {@code register()}。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SkillSdkProperties.class)
public class SkillSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillSdkProperties properties,
                                       AutowireCapableBeanFactory beanFactory) {
        return new SkillRegistry(properties, beanFactory);
    }
}

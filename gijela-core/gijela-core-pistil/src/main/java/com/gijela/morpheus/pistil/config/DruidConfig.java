package com.gijela.morpheus.pistil.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Druid 数据源手动配置（替代 druid-spring-boot-starter）
 */
@Configuration
public class DruidConfig {

    private static final Logger log = LoggerFactory.getLogger(DruidConfig.class);

    // ========== 监控配置 ==========
    @Value("${spring.datasource.druid.stat-view-servlet.enabled:false}")
    private boolean statViewEnabled;

    @Value("${spring.datasource.druid.stat-view-servlet.url-pattern:/druid/*}")
    private String statViewUrlPattern;

    @Value("${spring.datasource.druid.stat-view-servlet.login-username}")
    private String loginUsername;

    @Value("${spring.datasource.druid.stat-view-servlet.login-password}")
    private String loginPassword;

    @Value("${spring.datasource.druid.stat-view-servlet.allow:}")
    private String allow;

    @Value("${spring.datasource.druid.stat-view-servlet.deny:}")
    private String deny;

    @Value("${spring.datasource.druid.web-stat-filter.enabled:false}")
    private boolean webStatFilterEnabled;

    @Value("${spring.datasource.druid.web-stat-filter.url-pattern:/*}")
    private String webStatFilterUrlPattern;

    @Value("${spring.datasource.druid.web-stat-filter.exclusions:*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*}")
    private String exclusions;

    /**
     * 创建 DruidDataSource（Primary 表示这是主数据源）
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public DataSource dataSource() {
        DruidDataSource dataSource = new DruidDataSource();

        log.info("===== Druid DataSource 初始化完成 =====");
        log.info("URL: {}", dataSource.getUrl());
        log.info("初始连接数: {}", dataSource.getInitialSize());
        log.info("最小空闲连接: {}", dataSource.getMinIdle());
        log.info("最大活跃连接: {}", dataSource.getMaxActive());
        log.info("最大等待时间: {}ms", dataSource.getMaxWait());

        return dataSource;
    }

}

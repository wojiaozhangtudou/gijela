package com.gijela.morpheus.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/** JWT 配置属性 (公共模块) */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String secret;              // 密钥（建议使用 Base64 64+字节）
    private int accessExpMinutes = 30;  // Access Token 过期(分钟)
    private int refreshExpDays = 7;     // Refresh Token 过期(天)
    private boolean enabled = true;     // 是否启用自动安全过滤
    private String tokenHeader = "Authorization"; // 读取 token 的请求头
    private String tokenPrefix = "Bearer ";       // 前缀（有空格）
    private List<String> permitUrls = new ArrayList<>(); // 直接放行的 URL 模式
    private boolean refreshEndpointEnabled = false; // 是否暴露公共刷新端点
    private String redisAuthorityKeyPrefix = "security:auth:perm:"; // Redis 权限集合 key 前缀，后接用户名
    private boolean requireAuthorityInRedis = false; // 若为 true 且未取到权限则拒绝认证
    private boolean enableTokenVersionCheck = false; // 是否启用 tokenVersion 校验
    private String userVersionKeyPrefix = "security:user:"; // 用户版本 Redis key 前缀 (key = prefix + username + ":version")
    private boolean autoIncrementOnPermissionChange = true; // 权限变更后是否自动递增版本

    public JwtProperties(){
        // 默认白名单
        permitUrls.add("/auth/login");
        permitUrls.add("/auth/refresh");
        permitUrls.add("/actuator/health");
        // 兼容带 /api 前缀的路径，避免因前缀不一致导致登录接口被拦截
        permitUrls.add("/api/v1/auth/login");
        permitUrls.add("/api/v1/auth/refresh");
        // 兼容历史短路径
        permitUrls.add("/v1/auth/login");
        permitUrls.add("/v1/auth/refresh");
        // 默认放行文档与 Swagger/OpenAPI 相关路径，避免鉴权拦截
        permitUrls.add("/doc.html");
        permitUrls.add("/swagger-ui.html");
        permitUrls.add("/swagger-ui/**");
        permitUrls.add("/v3/api-docs/**");
        permitUrls.add("/swagger-resources/**");
        permitUrls.add("/webjars/**");
    }

    public String getSecret(){return secret;} public void setSecret(String secret){this.secret=secret;}
    public int getAccessExpMinutes(){return accessExpMinutes;} public void setAccessExpMinutes(int accessExpMinutes){this.accessExpMinutes=accessExpMinutes;}
    public int getRefreshExpDays(){return refreshExpDays;} public void setRefreshExpDays(int refreshExpDays){this.refreshExpDays=refreshExpDays;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public String getTokenHeader(){return tokenHeader;} public void setTokenHeader(String tokenHeader){this.tokenHeader=tokenHeader;}
    public String getTokenPrefix(){return tokenPrefix;} public void setTokenPrefix(String tokenPrefix){this.tokenPrefix=tokenPrefix;}
    public List<String> getPermitUrls(){return permitUrls;} public void setPermitUrls(List<String> permitUrls){ if(!CollectionUtils.isEmpty(permitUrls)){ this.permitUrls = permitUrls; } }
    public boolean isRefreshEndpointEnabled(){return refreshEndpointEnabled;} public void setRefreshEndpointEnabled(boolean refreshEndpointEnabled){this.refreshEndpointEnabled = refreshEndpointEnabled;}
    public String getRedisAuthorityKeyPrefix(){return redisAuthorityKeyPrefix;} public void setRedisAuthorityKeyPrefix(String redisAuthorityKeyPrefix){this.redisAuthorityKeyPrefix=redisAuthorityKeyPrefix;}
    public boolean isRequireAuthorityInRedis(){return requireAuthorityInRedis;} public void setRequireAuthorityInRedis(boolean requireAuthorityInRedis){this.requireAuthorityInRedis=requireAuthorityInRedis;}
    public boolean isEnableTokenVersionCheck(){return enableTokenVersionCheck;} public void setEnableTokenVersionCheck(boolean v){this.enableTokenVersionCheck=v;}
    public String getUserVersionKeyPrefix(){return userVersionKeyPrefix;} public void setUserVersionKeyPrefix(String userVersionKeyPrefix){this.userVersionKeyPrefix=userVersionKeyPrefix;}
    public boolean isAutoIncrementOnPermissionChange(){return autoIncrementOnPermissionChange;} public void setAutoIncrementOnPermissionChange(boolean v){this.autoIncrementOnPermissionChange=v;}
}

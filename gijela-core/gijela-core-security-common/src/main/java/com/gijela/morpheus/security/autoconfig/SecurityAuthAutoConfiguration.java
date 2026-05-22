package com.gijela.morpheus.security.autoconfig;

import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.security.common.JwtUtil;
import com.gijela.morpheus.security.config.JwtProperties;
import com.gijela.morpheus.security.auth.AuthoritiesProvider;
import com.gijela.morpheus.security.permission.AuthxPermissionHelper;
import com.gijela.morpheus.security.auth.RedisAuthoritiesProvider;
import com.gijela.morpheus.security.filter.JwtAuthenticationFilter;
import com.gijela.morpheus.security.token.TokenVersionValidator;
import com.gijela.morpheus.common.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnClass({SecurityFilterChain.class, JwtUtil.class})
@ConditionalOnProperty(prefix = "security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAuthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuthAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthoritiesProvider authoritiesProvider(StringRedisTemplate stringRedisTemplate, JwtProperties props){
        return new RedisAuthoritiesProvider(stringRedisTemplate, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(){
        return new JwtUtil();
    }


    @Bean
    @ConditionalOnBean(AuthoritiesProvider.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                           JwtProperties props,
                                                           AuthoritiesProvider authoritiesProvider,
                                                           ObjectProvider<TokenVersionValidator> validatorProvider,
                                                           ObjectProvider<StringRedisTemplate> redisProvider){
        TokenVersionValidator validator = validatorProvider.getIfAvailable();
        StringRedisTemplate redisTemplate = redisProvider.getIfAvailable();
        log.info("[security-common] 注册 JwtAuthenticationFilter(redisOnly) accessExpMinutes={} refreshExpDays={} redisPrefix={} tokenVersionCheck={} ", props.getAccessExpMinutes(), props.getRefreshExpDays(), props.getRedisAuthorityKeyPrefix(), props.isEnableTokenVersionCheck());
        return new JwtAuthenticationFilter(jwtUtil, props, authoritiesProvider, validator, redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint authenticationEntryPoint(){
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException) throws IOException {
                writeJson(response, ApiResponse.fail(ErrorCode.UNAUTHORIZED));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler accessDeniedHandler(){
        return (request, response, accessDeniedException) -> writeJson(response, ApiResponse.fail(ErrorCode.FORBIDDEN));
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = toJson(body);
        response.getWriter().write(json);
    }

    // 简单 JSON 序列化（避免引入额外依赖），仅处理 ApiResponse 结构
    private String toJson(Object obj){
        if (obj instanceof ApiResponse<?> ar){
            String dataPart = ar.getData()==null?"null":"\""+escape(ar.getData().toString())+"\"";
            return "{"+
                    "\"code\":"+ar.getCode()+","+
                    "\"msg\":\""+escape(ar.getMsg())+"\""+","+
                    "\"data\":"+dataPart+","+
                    "\"timestamp\":"+ar.getTimestamp()+","+
                    "\"traceId\":\""+escape(String.valueOf(ar.getTraceId()))+"\""+
                    "}";
        }
        return "{}";
    }
    private String escape(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }

    @Bean(name = "commonSecurityFilterChain")
    @ConditionalOnBean(JwtAuthenticationFilter.class)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain commonSecurityFilterChain(HttpSecurity http,
                                                         JwtAuthenticationFilter jwtFilter,
                                                         JwtProperties props,
                                                         AuthenticationEntryPoint entryPoint,
                                                         AccessDeniedHandler deniedHandler) throws Exception {
         List<String> permits = props.getPermitUrls();
         http.csrf(csrf->csrf.disable())
                 .sessionManagement(sm->sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                 .authorizeHttpRequests(reg->{
                     if (permits!=null && !permits.isEmpty()) {
                         reg.requestMatchers(permits.toArray(String[]::new)).permitAll();
                     }
                     reg.anyRequest().authenticated();
                 })
                 .exceptionHandling(eh->eh.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                 .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                 .addFilterBefore(new SecurityDebugFilter(), org.springframework.security.web.access.intercept.AuthorizationFilter.class);
         return http.build();
     }

    @Bean(name = "authx")
    @ConditionalOnMissingBean(name = "authx")
    public AuthxPermissionHelper authxPermissionHelper(){
        return new AuthxPermissionHelper();
    }

    // 简单调试过滤器
    static class SecurityDebugFilter extends OncePerRequestFilter {
        private static final Logger logger = LoggerFactory.getLogger(SecurityDebugFilter.class);
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (logger.isDebugEnabled()) {
                logger.debug("[SEC-DEBUG] uri={} method={} authPresent={} principal={} authorities={}", request.getRequestURI(), request.getMethod(), auth != null, auth == null ? null : auth.getPrincipal(), auth == null ? null : auth.getAuthorities());
            }
            filterChain.doFilter(request, response);
        }
    }
}

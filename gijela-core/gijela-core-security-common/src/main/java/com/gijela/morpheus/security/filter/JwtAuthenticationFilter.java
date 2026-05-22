package com.gijela.morpheus.security.filter;

import com.gijela.morpheus.security.common.JwtUtil;
import com.gijela.morpheus.security.config.JwtProperties;
import com.gijela.morpheus.security.auth.AuthoritiesProvider;
import com.gijela.morpheus.security.auth.JwtAuthUser;
import com.gijela.morpheus.security.token.TokenVersionValidator;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 通用 JWT 认证过滤器 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwtUtil;
    private final JwtProperties props;
    private final AuthoritiesProvider authoritiesProvider;
    private final TokenVersionValidator tokenVersionValidator; // 可为空
    private final StringRedisTemplate redisTemplate; // 可为空（无 Redis 时跳过黑名单校验）

    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtProperties props, AuthoritiesProvider authoritiesProvider){
        this(jwtUtil, props, authoritiesProvider, null, null);
    }
    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtProperties props, AuthoritiesProvider authoritiesProvider, TokenVersionValidator tokenVersionValidator){
        this(jwtUtil, props, authoritiesProvider, tokenVersionValidator, null);
    }
    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtProperties props, AuthoritiesProvider authoritiesProvider, TokenVersionValidator tokenVersionValidator, StringRedisTemplate redisTemplate){
        this.jwtUtil = jwtUtil; this.props = props; this.authoritiesProvider = authoritiesProvider; this.tokenVersionValidator = tokenVersionValidator;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // 确保异步调度 (ASYNC) 也继续执行过滤器，以便 SSE 等场景保持认证
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 跳过预检请求，避免因浏览器 OPTIONS 请求未携带 token 而被判为未认证
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (log.isDebugEnabled()) {
            log.debug("[JwtAuthenticationFilter] dispatcherType={} uri={} method={} resolvedTokenPresent={} ctxAuthPresent={}",
                    request.getDispatcherType(), request.getRequestURI(), request.getMethod(), StringUtils.hasText(token), SecurityContextHolder.getContext().getAuthentication()!=null);
        }
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication()==null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                Object typ = claims.get("typ");
                if (typ!=null && "access".equals(typ.toString())) {
                    String jti = jwtUtil.getJti(claims);
                    if (isBlacklisted(jti)) {
                        log.debug("access token 命中黑名单 jti={}", jti);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (props.isEnableAccessSessionCheck()) {
                        String sid = claims.get("sid", String.class);
                        if (!isSessionActive(sid)) {
                            log.debug("access token 会话已失效 sid={}", sid);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }
                    String subject = claims.getSubject();
                    Long userId = null;
                    try { if (StringUtils.hasText(subject)) userId = Long.parseLong(subject); } catch (NumberFormatException ignore) {}
                    String username = claims.get("username", String.class);
                    if (!StringUtils.hasText(username) && userId!=null) {
                        // 若 token 中没有 username，可允许业务用 userId 反查(当前需求仅 redis，不做 DB 查询，跳过)
                        username = String.valueOf(userId);
                    }
                    if (StringUtils.hasText(username)) {
                        Integer tokenVersionInToken = null;
                        Object tvObj = claims.get("tokenVersion");
                        if (tvObj != null) {
                            try { tokenVersionInToken = Integer.parseInt(tvObj.toString()); } catch (NumberFormatException ignore) {}
                        }
                        if (props.isEnableTokenVersionCheck() && tokenVersionValidator != null) {
                            if (!tokenVersionValidator.isValid(username, tokenVersionInToken)) {
                                log.debug("tokenVersion 校验失败 username={} tokenVersionInToken={}", username, tokenVersionInToken);
                                filterChain.doFilter(request, response); return; // 不认证直接放行后续 -> 最终 401
                            }
                        }
                        var authorities = authoritiesProvider.load(username, userId);
                        if (props.isRequireAuthorityInRedis() && (authorities==null || authorities.isEmpty())) {
                            log.debug("Redis 未找到权限且 requireAuthorityInRedis=true, 跳过认证 username={}", username);
                        } else {
                            JwtAuthUser user = new JwtAuthUser(userId, username, authorities);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("JWT 解析/认证失败: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request){
        String header = props.getTokenHeader();
        String prefix = props.getTokenPrefix();
        String auth = request.getHeader(header);
        if (!StringUtils.hasText(auth)) {
            // 尝试常见的 Authorization header 名称
            auth = request.getHeader("Authorization");
        }
        if (StringUtils.hasText(auth)) {
            if (StringUtils.hasText(prefix) && auth.startsWith(prefix)) {
                return auth.substring(prefix.length());
            }
            return auth;
        }
        if (props.isAllowQueryToken()) {
            String p = request.getParameter("token");
            if (!StringUtils.hasText(p)) p = request.getParameter("access_token");
            if (StringUtils.hasText(p)) return p;
        }
        if (props.isAllowCookieToken() && request.getCookies()!=null) {
            for (Cookie c: request.getCookies()) {
                if ("token".equals(c.getName()) || "access_token".equals(c.getName())) {
                    String v = c.getValue();
                    if (StringUtils.hasText(v)) return v;
                }
            }
        }
        return null;
    }

    private boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti) || redisTemplate == null) return false;
        try {
            String key = props.getBlacklistKeyPrefix() + jti;
            Boolean hasKey = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.warn("查询 access 黑名单失败 jti={} err={}", jti, e.getMessage());
            return false;
        }
    }

    private boolean isSessionActive(String sid) {
        // 兼容旧 token（无 sid）或无 Redis 场景，不拦截
        if (!StringUtils.hasText(sid) || redisTemplate == null) return true;
        try {
            String key = props.getRefreshKeyPrefix() + "session:" + sid;
            Boolean hasKey = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.warn("查询会话状态失败 sid={} err={}", sid, e.getMessage());
            return true;
        }
    }
}

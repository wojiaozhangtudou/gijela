package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.security.common.JwtUtil;
import com.gijela.morpheus.security.config.JwtProperties;
import com.gijela.morpheus.pistil.domain.dto.LoginDTO;
import com.gijela.morpheus.pistil.domain.dto.LoginSessionPageDTO;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.domain.vo.LoginVO;
import com.gijela.morpheus.pistil.domain.vo.LoginSessionVO;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysUserMapper;
import com.gijela.morpheus.pistil.service.IAuthService;
import com.gijela.morpheus.pistil.service.ISysUserService;
import com.gijela.morpheus.pistil.service.ISysUserRoleService;
import com.gijela.morpheus.pistil.service.ISysRoleMenuService;
import com.gijela.morpheus.pistil.service.ISysMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleService;
import com.gijela.morpheus.pistil.service.PermissionAggregationService;
import com.gijela.morpheus.pistil.service.RefreshTokenService;
import com.gijela.morpheus.pistil.service.TokenVersionService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired private SysUserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ISysUserService userService;
    @Autowired private ISysUserRoleService userRoleService;
    @Autowired private ISysRoleMenuService roleMenuService;
    @Autowired private ISysMenuService menuService;
    @Autowired private ISysRoleService roleService;
    @Autowired private PermissionAggregationService permissionAggregationService;
    @Autowired private TokenVersionService tokenVersionService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    // ========== 对外接口 ==========

    @Override
    public LoginVO login(LoginDTO dto, String clientLabel, String deviceNo, String loginIp) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "用户名或密码错误");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ILLEGAL_STATE, "账号已禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "用户名或密码错误");
        }
        assertSessionLimit(user);
        permissionAggregationService.aggregateAndCache(user.getId(), user.getUsername());
        return buildLoginResponse(user, null, clientLabel, deviceNo, loginIp);
    }

    @Override
    public UserVO currentUser(String token) {
        Long uid = parseAccessUid(token);
        return userService.getUserDetail(uid);
    }

    @Override
    public List<MenuTreeVO> currentUserMenus(String token) {
        Long uid = parseAccessUid(token);
        return menuService.buildMenuTreeForUser(uid);
    }

    @Override
    public List<String> currentUserPerms(String token) {
        Long uid = parseAccessUid(token);
        List<Long> roleIds = userRoleService
                .list(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, uid))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) return Collections.emptyList();
        List<SysRole> roles = roleService.list(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds));
        // SYS_ADMIN 返回所有启用菜单的非空权限标识
        if (roles.stream().anyMatch(r -> "SYS_ADMIN".equals(r.getCode()))) {
            return menuService.list(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getStatus, 1))
                    .stream().map(SysMenu::getPermission)
                    .filter(p -> p != null && !p.isBlank())
                    .distinct().collect(Collectors.toList());
        }
        List<Long> menuIds = roleMenuService
                .list(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        if (menuIds.isEmpty()) return Collections.emptyList();
        return menuService.list(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getId, menuIds).eq(SysMenu::getStatus, 1))
                .stream().map(SysMenu::getPermission)
                .filter(p -> p != null && !p.isBlank())
                .distinct().collect(Collectors.toList());
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        Claims claims = jwtUtil.parseToken(stripBearer(refreshToken));
        Object typ = claims.get("typ");
        if (typ == null || !"refresh".equals(typ.toString())) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌类型错误");
        }
        Long uid = parseSubject(claims);
        String sessionId = claims.get("sid", String.class);
        String refreshJti = jwtUtil.getJti(claims);
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(refreshJti)) {
            throw new BizException(ErrorCode.SESSION_INVALIDATED, "会话已失效");
        }
        if (!refreshTokenService.validateSession(sessionId, refreshJti)) {
            throw new BizException(ErrorCode.SESSION_INVALIDATED, "会话已失效");
        }
        SysUser user = userMapper.selectById(uid);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        if (user.getStatus() != null && user.getStatus() == 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "账号已禁用");
        permissionAggregationService.aggregateAndCache(user.getId(), user.getUsername());
        return buildLoginResponse(user, sessionId, null, null, null);
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) return;
        try {
            Claims claims = jwtUtil.parseToken(stripBearer(token));
            Object typ = claims.get("typ");
            if (typ == null || !"access".equals(typ.toString())) return;
            String username = claims.get("username", String.class);
            String sessionId = claims.get("sid", String.class);
            String jti = jwtUtil.getJti(claims);
            if (!StringUtils.hasText(username)) return;
            if (StringUtils.hasText(sessionId)) {
                refreshTokenService.revokeSession(sessionId);
            }
            addAccessToBlacklist(jti, claims.getExpiration());
            tokenVersionService.increment(username);
            try {
                stringRedisTemplate.delete("security:auth:perm:" + username);
            } catch (Exception e) {
                log.debug("登出时清理权限缓存失败, username={}, err={}", username, e.getMessage());
            }
        } catch (Exception e) {
            log.debug("登出时解析 token 失败, err={}", e.getMessage());
        }
    }

    @Override
    public List<LoginSessionVO> listUserSessions(Long userId) {
        if (userId == null) return refreshTokenService.listAllSessions();
        return refreshTokenService.listSessions(userId);
    }

    @Override
    public Page<LoginSessionVO> pageSessions(LoginSessionPageDTO dto) {
        int current = dto == null ? 1 : Math.max(1, dto.getCurrent());
        int size = dto == null ? 20 : Math.max(1, dto.getSize());
        Long userId = dto == null ? null : dto.getUserId();
        String username = dto == null ? null : dto.getUsername();

        List<LoginSessionVO> full = userId == null
                ? refreshTokenService.listAllSessions()
                : refreshTokenService.listSessions(userId);

        if (StringUtils.hasText(username)) {
            String q = username.trim().toLowerCase(Locale.ROOT);
            full = full.stream()
                    .filter(s -> StringUtils.hasText(s.getUsername()) && s.getUsername().toLowerCase(Locale.ROOT).contains(q))
                    .collect(Collectors.toList());
        }

        long total = full.size();
        int from = (current - 1) * size;
        List<LoginSessionVO> records;
        if (from >= full.size()) {
            records = List.of();
        } else {
            int to = Math.min(from + size, full.size());
            records = full.subList(from, to);
        }

        Page<LoginSessionVO> page = new Page<>(current, size, total);
        page.setRecords(records);
        return page;
    }

    @Override
    public void kickoutSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "sessionId 不能为空");
        }
        refreshTokenService.revokeSession(sessionId);
    }

    @Override
    public void kickoutAllSessions(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "userId 不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        refreshTokenService.revokeAllSessions(userId);
        if (StringUtils.hasText(user.getUsername())) {
            tokenVersionService.increment(user.getUsername());
            try {
                stringRedisTemplate.delete("security:auth:perm:" + user.getUsername());
            } catch (Exception e) {
                log.debug("踢出全部会话时清理权限缓存失败, username={}, err={}", user.getUsername(), e.getMessage());
            }
        }
    }

    // ========== 私有辅助方法 ==========

    /** 去掉 Bearer 前缀 */
    private String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    /** 解析 access token 并返回 uid，token 无效时抛出 BizException */
    private Long parseAccessUid(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        Claims claims = jwtUtil.parseToken(stripBearer(rawToken));
        Object typ = claims.get("typ");
        if (typ == null || !"access".equals(typ.toString())) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌类型错误");
        }
        return parseSubject(claims);
    }

    /** 解析 claims subject 为 Long uid */
    private Long parseSubject(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌无效");
        }
    }

    /** 获取或初始化用户的 tokenVersion */
    private int getOrInitVersion(SysUser user) {
        Integer version = tokenVersionService.get(user.getUsername());
        if (version == null) {
            version = tokenVersionService.ensureInitialized(user.getUsername(), user.getTokenVersion());
        }
        return version == null ? 0 : version;
    }

    private void assertSessionLimit(SysUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        int maxSessions = jwtProperties.getMaxSessionsPerUser();
        if (maxSessions <= 0) {
            return;
        }
        int activeSessions = refreshTokenService.listSessions(user.getId()).size();
        if (activeSessions >= maxSessions) {
            throw new BizException(
                    ErrorCode.CONFLICT,
                    String.format("当前账号最多允许同时在线 %d 个会话，请先在会话管理中删除旧设备后再登录", maxSessions)
            );
        }
    }

    /** 构建登录/刷新响应 VO */
    private LoginVO buildLoginResponse(SysUser user, String sessionId, String clientLabel, String deviceNo, String loginIp) {
        int version = getOrInitVersion(user);
        String sid = sessionId;
        String refresh;
        if (!StringUtils.hasText(sid)) {
            refresh = jwtUtil.generateRefreshToken(user.getId(), null);
            Claims refreshClaims = jwtUtil.parseToken(refresh);
            String refreshJti = jwtUtil.getJti(refreshClaims);
            String label = StringUtils.hasText(clientLabel) ? clientLabel : "web";
            String newSid = refreshTokenService.createSession(user.getId(), user.getUsername(), refreshJti, jwtUtil.refreshExpireAt(), label, deviceNo, loginIp);
            sid = newSid;
            // 用 sid 重新签发 refresh，保证 token 与服务端会话一一对应
            refresh = jwtUtil.generateRefreshToken(user.getId(), sid);
            Claims realClaims = jwtUtil.parseToken(refresh);
            refreshTokenService.refreshSession(sid, jwtUtil.getJti(realClaims), jwtUtil.refreshExpireAt());
        } else {
            refresh = jwtUtil.generateRefreshToken(user.getId(), sid);
            Claims refreshClaims = jwtUtil.parseToken(refresh);
            refreshTokenService.refreshSession(sid, jwtUtil.getJti(refreshClaims), jwtUtil.refreshExpireAt());
            refreshTokenService.touchSession(sid);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("nickname", user.getNickname());
        extra.put("tokenVersion", version);
        extra.put("sid", sid);
        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), extra);
        LoginVO vo = new LoginVO();
        vo.setAccessToken(access);
        vo.setRefreshToken(refresh);
        vo.setAccessExpireAt(jwtUtil.accessExpireAt());
        vo.setRefreshExpireAt(jwtUtil.refreshExpireAt());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setSessionId(sid);
        return vo;
    }

    private void addAccessToBlacklist(String jti, Date exp) {
        if (!StringUtils.hasText(jti) || exp == null) return;
        try {
            long ttlMillis = exp.getTime() - System.currentTimeMillis();
            if (ttlMillis <= 0) return;
            String key = jwtProperties.getBlacklistKeyPrefix() + jti;
            stringRedisTemplate.opsForValue().set(key, "1", ttlMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("写入 access 黑名单失败 jti={} err={}", jti, e.getMessage());
        }
    }
}

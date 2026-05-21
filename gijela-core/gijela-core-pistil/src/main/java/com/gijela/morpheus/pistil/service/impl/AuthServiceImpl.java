package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.security.common.JwtUtil;
import com.gijela.morpheus.pistil.domain.dto.LoginDTO;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.domain.vo.LoginVO;
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
    @Autowired private StringRedisTemplate stringRedisTemplate;

    // ========== 对外接口 ==========

    @Override
    public LoginVO login(LoginDTO dto) {
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
        permissionAggregationService.aggregateAndCache(user.getId(), user.getUsername());
        return buildLoginResponse(user);
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
        SysUser user = userMapper.selectById(uid);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        if (user.getStatus() != null && user.getStatus() == 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "账号已禁用");
        permissionAggregationService.aggregateAndCache(user.getId(), user.getUsername());
        return buildLoginResponse(user);
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) return;
        try {
            Claims claims = jwtUtil.parseToken(stripBearer(token));
            Object typ = claims.get("typ");
            if (typ == null || !"access".equals(typ.toString())) return;
            String username = claims.get("username", String.class);
            if (!StringUtils.hasText(username)) return;
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

    /** 构建登录/刷新响应 VO */
    private LoginVO buildLoginResponse(SysUser user) {
        int version = getOrInitVersion(user);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("nickname", user.getNickname());
        extra.put("tokenVersion", version);
        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), extra);
        String refresh = jwtUtil.generateRefreshToken(user.getId());
        LoginVO vo = new LoginVO();
        vo.setAccessToken(access);
        vo.setRefreshToken(refresh);
        vo.setAccessExpireAt(jwtUtil.accessExpireAt());
        vo.setRefreshExpireAt(jwtUtil.refreshExpireAt());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}

package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.mapper.SysUserRoleMapper;
import com.gijela.morpheus.pistil.service.ISysMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleService;
import com.gijela.morpheus.pistil.service.PermissionAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 聚合权限: 角色 -> 菜单 -> 权限点, 并写入 Redis Set */
@Service
public class PermissionAggregationServiceImpl implements PermissionAggregationService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAggregationServiceImpl.class);

    @Autowired private SysUserRoleMapper userRoleMapper;
    @Autowired private ISysRoleService roleService;
    @Autowired private ISysRoleMenuService roleMenuService;
    @Autowired private ISysMenuService menuService;
    @Autowired private StringRedisTemplate redisTemplate;
    // 移除 JwtProperties 直接依赖，使用配置前缀
    @Value("${security.jwt.redis-authority-key-prefix:security:auth:perm:}")
    private String redisAuthorityKeyPrefix;

    @Override
    public Set<String> aggregateAndCache(Long userId, String username) {
        if (userId == null || username == null || username.isBlank()) return Collections.emptySet();
        // 用户角色
        List<SysUserRole> urs = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (urs.isEmpty()) {
            writeRedis(username, Collections.emptySet());
            return Collections.emptySet();
        }
        List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).distinct().toList();
        List<SysRole> roles = roleService.list(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds));
        boolean isAdmin = roles.stream().anyMatch(r -> "SYS_ADMIN".equals(r.getCode()));
        Set<String> result = new HashSet<>();
        // 角色名称加入（Spring Security 角色惯例: ROLE_ 前缀）
        roles.forEach(r -> {
            if (r.getCode()!=null && !r.getCode().isBlank()) result.add("ROLE_" + r.getCode());
        });
        List<SysMenu> menus;
        if (isAdmin) {
            menus = menuService.list(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getStatus, 1));
        } else {
            List<Long> menuIds = roleMenuService
                    .list(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).distinct().toList();
            if (menuIds.isEmpty()) {
                writeRedis(username, result);
                return result;
            }
            menus = menuService.list(Wrappers.<SysMenu>lambdaQuery()
                    .in(SysMenu::getId, menuIds).eq(SysMenu::getStatus, 1));
        }
        menus.stream().map(SysMenu::getPermission)
                .filter(p -> p!=null && !p.isBlank())
                .forEach(result::add);
        writeRedis(username, result);
        return result;
    }


    private void writeRedis(String username, Set<String> perms){
        try {
            String key = redisAuthorityKeyPrefix + username;
            redisTemplate.delete(key);
            if (perms!=null && !perms.isEmpty()) {
                redisTemplate.opsForSet().add(key, perms.toArray(String[]::new));
            }
        } catch (Exception e) {
            log.warn("写入权限缓存失败, username={}, err={}", username, e.getMessage());
        }
    }
}

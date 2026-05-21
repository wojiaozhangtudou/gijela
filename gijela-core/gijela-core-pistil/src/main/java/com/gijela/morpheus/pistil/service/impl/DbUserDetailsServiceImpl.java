package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.mapper.SysUserMapper;
import com.gijela.morpheus.pistil.service.ISysMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleService;
import com.gijela.morpheus.pistil.service.ISysUserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于数据库的 UserDetailsService：从用户表及用户角色表加载用户和角色信息，用于认证（HTTP Basic / 其它）。
 * 扩展：同时将菜单的 permission 字符串也作为 GrantedAuthority，方便在 @PreAuthorize 中使用 hasAuthority('perm') 检查。
 */
@Service
public class DbUserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private ISysUserRoleService userRoleService;
    @Autowired
    private ISysRoleService roleService;
    @Autowired
    private ISysRoleMenuService roleMenuService;
    @Autowired
    private ISysMenuService menuService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("用户名为空");
        }
        SysUser u = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)
        );
        if (u == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        // 状态校验：1 正常，0 禁用
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new DisabledException("账号已禁用");
        }
        // 加载角色
        List<SysUserRole> urs = userRoleService.list(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, u.getId())
        );
        List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleIds.isEmpty() ? List.of() : roleService.listByIds(roleIds);
        List<GrantedAuthority> auths = new ArrayList<>();
        for (SysRole r : roles) {
            if (r == null || r.getCode() == null) {
                continue;
            }
            // Spring Security 角色以 ROLE_ 前缀约定
            auths.add(new SimpleGrantedAuthority("ROLE_" + r.getCode()));
        }
        // 额外：把菜单的 permission 作为权限字符串注入，便于使用 hasAuthority('perm')
        if (!roleIds.isEmpty()) {
            // 若包含系统管理员角色（SYS_ADMIN），加载所有启用菜单的 permission
            boolean isAdmin = roles.stream().anyMatch(r -> "SYS_ADMIN".equals(r.getCode()));
            Set<String> perms;
            if (isAdmin) {
                perms = menuService.list(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getStatus, 1))
                        .stream().map(SysMenu::getPermission)
                        .filter(p -> p != null && !p.isBlank())
                        .collect(Collectors.toSet());
            } else {
                List<Long> menuIds = roleMenuService
                        .list(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                        .stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
                perms = menuService
                        .list(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getId, menuIds)
                                .eq(SysMenu::getStatus, 1))
                        .stream().map(SysMenu::getPermission)
                        .filter(p -> p != null && !p.isBlank())
                        .collect(Collectors.toSet());
            }
            for (String p : perms) {
                auths.add(new SimpleGrantedAuthority(p));
            }
        }
        // 返回 Spring Security 自带 User 实现
        return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                .password(u.getPassword() == null ? "" : u.getPassword())
                .authorities(auths)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(u.getStatus() != null && u.getStatus() == 0)
                .build();
    }
}

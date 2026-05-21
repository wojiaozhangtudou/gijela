package com.gijela.morpheus.security.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 业务模块实现该接口以提供用户数据。
 * 可仅实现 loadUserByUsername；loadUserById 可选。
 */
public interface AuthUserDetailsService extends UserDetailsService {
    /** 可选：按用户ID加载（若不支持返回 null） */
    default UserDetails loadUserById(Long userId) {
        return null;
    }
}

package com.gijela.morpheus.security.auth;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** 仅从外部(如 Redis)加载权限的提供者 */
public interface AuthoritiesProvider {
    Collection<? extends GrantedAuthority> load(String username, Long userId);
}

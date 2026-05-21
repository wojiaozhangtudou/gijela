package com.gijela.morpheus.security.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 自定义 MethodSecurityExpressionRoot，提供 hasRoleOrAuthority/hasAnyRoleOrAuthority 简化表达式。
 */
public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot implements MethodSecurityExpressionOperations {
    private static final Logger log = LoggerFactory.getLogger(CustomMethodSecurityExpressionRoot.class);
    private Object filterObject;
    private Object returnObject;

    public CustomMethodSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }

    public boolean hasRoleOrAuthority(String roleOrAuthority) {
        if (roleOrAuthority == null) {
            return false;
        }
        Authentication auth = getAuthentication();
        if (auth == null) {
            log.info("[security-expr] hasRoleOrAuthority('{}') called but authentication is null", roleOrAuthority);
        } else {
            log.info("[security-expr] hasRoleOrAuthority('{}') called for user={} authorities={}", roleOrAuthority, auth.getName(), auth.getAuthorities());
        }
        return hasRoleExact(roleOrAuthority) || hasAuthorityExact(roleOrAuthority);
    }

    public boolean hasAnyRoleOrAuthority(String... items) {
        if (items == null || items.length == 0) {
            return false;
        }
        for (String it : items) {
            if (hasRoleOrAuthority(it)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRoleExact(String role) {
        if (role == null) {
            return false;
        }
        String r = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        Collection<? extends GrantedAuthority> auths = getAuthentication() == null ? null : getAuthentication().getAuthorities();
        if (auths == null) {
            return false;
        }
        for (GrantedAuthority ga : auths) {
            if (r.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAuthorityExact(String authority) {
        if (authority == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> auths = getAuthentication() == null ? null : getAuthentication().getAuthorities();
        if (auths == null) {
            return false;
        }
        for (GrantedAuthority ga : auths) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getFilterObject() {
        return this.filterObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getReturnObject() {
        return this.returnObject;
    }

    @Override
    public Object getThis() {
        return this;
    }
}

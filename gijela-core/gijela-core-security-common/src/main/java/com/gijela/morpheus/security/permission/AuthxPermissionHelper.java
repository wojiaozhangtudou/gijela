package com.gijela.morpheus.security.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * 备用权限表达式 Bean：通过 @authx.hasRoleOrAuthority('perm') 方式使用。
 */
public class AuthxPermissionHelper {
    private static final Logger log = LoggerFactory.getLogger(AuthxPermissionHelper.class);

    public boolean hasRoleOrAuthority(String value) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.debug("[authx] authentication is null");
            return false;
        }
        Collection<? extends GrantedAuthority> list = auth.getAuthorities();
        if (list == null || list.isEmpty()) {
            return false;
        }
        String roleVariant = value.startsWith("ROLE_") ? value : "ROLE_" + value;
        for (GrantedAuthority ga : list) {
            String a = ga.getAuthority();
            if (value.equals(a) || roleVariant.equals(a)) {
                log.debug("[authx] hasRoleOrAuthority '{}' hit by authority '{}'", value, a);
                return true;
            }
        }
        log.debug("[authx] hasRoleOrAuthority '{}' not matched. authorities={}", value, list);
        return false;
    }

    public boolean hasAnyRoleOrAuthority(String... values) {
        if (values == null || values.length == 0) {
            return false;
        }
        for (String v : values) {
            if (hasRoleOrAuthority(v)) {
                return true;
            }
        }
        return false;
    }
}

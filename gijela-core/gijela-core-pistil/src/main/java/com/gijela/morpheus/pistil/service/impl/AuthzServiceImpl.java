package com.gijela.morpheus.pistil.service.impl;

import com.gijela.morpheus.pistil.domain.dto.SaveUserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在 SpEL 中可通过 @authzService.checkSaveUser(#p0) 调用，用于在授权判断时打印日志并返回布尔结果。
 */
@Component("authzService")
public class AuthzServiceImpl {
    private static final Logger logger = LoggerFactory.getLogger(AuthzServiceImpl.class);

    public boolean checkSaveUser(SaveUserDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = auth == null ? "anonymous" : String.valueOf(auth.getName());
        Set<String> auths = auth == null
                ? Set.of()
                : auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
        Long id = dto == null ? null : dto.getId();
        String required = (id == null) ? "user:create" : "user:update";
        boolean allowed = false;
        // 若有 ROLE_SYS_ADMIN 则默认允许
        if (auths.contains("ROLE_SYS_ADMIN") || auths.contains("ROLE_SYS_ADMIN".toUpperCase())) {
            allowed = true;
        } else if (auths.contains(required)) {
            allowed = true;
        }
        logger.info(
                "AuthzService.checkSaveUser principal={} dto.id={} required={} hasAuthorities={} allowed={}",
                principal,
                id,
                required,
                auths,
                allowed
        );
        return allowed;
    }
}

package com.gijela.morpheus.pistil.event;

import org.springframework.context.ApplicationEvent;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色权限变更事件：当角色的菜单/权限发生调整时发布 */
public class RolePermissionChangedEvent extends ApplicationEvent {
    private final Set<Long> roleIds;
    public RolePermissionChangedEvent(Object source, Collection<Long> roleIds) {
        super(source);
        this.roleIds = roleIds==null?Set.of():roleIds.stream().filter(r->r!=null).collect(Collectors.toSet());
    }
    public Set<Long> getRoleIds(){return roleIds;}
}


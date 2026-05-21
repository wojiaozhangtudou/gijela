package com.gijela.morpheus.pistil.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/** 简化的权限变更事件发布器 */
@Component
public class PermissionChangePublisher {
    private static final Logger log = LoggerFactory.getLogger(PermissionChangePublisher.class);

    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * 发布在无事务内（txActive=false）：
     * 修复：确保 assignPerms 在事务内（方法上有 @Transactional，并且方法通过 Spring 代理调用，而非同类内部自调用）。
     * 自调用问题：若同类内调用了该方法，事务代理不会生效；把 publish 的调用放到另一个 bean 或改用 AspectJ/配置代理。
     */
    public void publishRolePermissionChanged(Collection<Long> roleIds){
        if (roleIds == null || roleIds.isEmpty()) return;
        try {
            // 在发布前记录事务状态
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
            log.info("[perm-publisher] publishing RolePermissionChangedEvent roleIds={} txActive={} syncActive={}", roleIds, txActive, syncActive);
            publisher.publishEvent(new RolePermissionChangedEvent(this, roleIds));
        } catch (Exception ex) {
            log.warn("[perm-publisher] failed to publish event for roleIds={} ex={}", roleIds, ex.toString());
        }
    }
}

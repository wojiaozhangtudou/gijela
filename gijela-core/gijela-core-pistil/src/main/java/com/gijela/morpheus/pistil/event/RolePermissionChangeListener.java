package com.gijela.morpheus.pistil.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.mapper.SysUserMapper;
import com.gijela.morpheus.pistil.mapper.SysUserRoleMapper;
import com.gijela.morpheus.pistil.service.PermissionAggregationService;
import com.gijela.morpheus.pistil.service.TokenVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 监听角色权限变更事件：
 * 1. 找出受影响用户
 * 2. 重新聚合权限写入 Redis
 * 3. (可选) 递增 tokenVersion 使旧 token 失效
 */
@Component
public class RolePermissionChangeListener {
    private static final Logger log = LoggerFactory.getLogger(RolePermissionChangeListener.class);

    @Autowired private SysUserRoleMapper userRoleMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private PermissionAggregationService permissionAggregationService;
    @Autowired private TokenVersionService tokenVersionService;
    // 使用 @Value 注入配置，避免依赖 v1 占位 JwtProperties 的方法
    @Value("${security.jwt.auto-increment-on-permission-change:true}")
    private boolean autoIncrementOnPermissionChange;

    @PostConstruct
    public void init() {
        log.info("[perm-listener] RolePermissionChangeListener initialized, autoIncrementOnPermissionChange={}", autoIncrementOnPermissionChange);
    }

    // 在事务提交后再处理并在独立线程池异步执行，避免阻塞调用线程
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // 无事务时也会执行，便于判断是事务缺失导致未触发还是其他问题。
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(0)
    @Async("permExecutor")
    public void onRolePermissionChanged(RolePermissionChangedEvent event) {
        // 打印一下当前线程池，以及当前线程名称，方便排查问题
        log.info("[perm-listener] 线程池={} 线程={}", Thread.currentThread().getThreadGroup().getName(), Thread.currentThread().getName());
        if (event == null || event.getRoleIds()==null || event.getRoleIds().isEmpty()) return;
        Set<Long> roleIds = event.getRoleIds();
        try {
            // 查询关联用户
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getRoleId, roleIds));
            if (userRoles.isEmpty()) {
                log.info("[perm-listener] 角色变更 roleIds={} 无关联用户", roleIds);
                return;
            }
            Set<Long> userIds = userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, userIds).ne(SysUser::getStatus, 0));
            if (users.isEmpty()) {
                log.info("[perm-listener] 角色变更 roleIds={} 关联用户全部禁用或不存在", roleIds);
                return;
            }
            boolean inc = autoIncrementOnPermissionChange;
            int total = users.size();
            int batchSize = 200;
            int processed = 0;
            for (int i=0;i<users.size();i+=batchSize) {
                List<SysUser> slice = users.subList(i, Math.min(i+batchSize, users.size()));
                for (SysUser u : slice) {
                    try {
                        permissionAggregationService.aggregateAndCache(u.getId(), u.getUsername());
                        if (inc) tokenVersionService.increment(u.getUsername());
                    } catch (Exception ex) {
                        log.warn("[perm-listener] 更新用户权限失败 user={} err={}", u.getUsername(), ex.getMessage());
                    }
                }
                processed += slice.size();
            }
            log.info("[perm-listener] 角色变更 roleIds={} 处理用户 {} 完成(递增版本={})", roleIds, total, inc);
        } catch (Exception e) {
            log.error("[perm-listener] 角色变更处理异常 roleIds={} err={}", roleIds, e.getMessage());
        }
    }
}

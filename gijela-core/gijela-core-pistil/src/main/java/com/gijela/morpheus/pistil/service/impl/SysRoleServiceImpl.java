package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.pistil.support.query.QueryWrapperBuilder;
import com.gijela.morpheus.pistil.support.sort.PageSortUtil;
import com.gijela.morpheus.pistil.support.sort.SortableFieldResolver;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.domain.vo.RoleVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysRoleMapper;
import com.gijela.morpheus.pistil.service.ISysRoleMenuService;
import com.gijela.morpheus.pistil.service.ISysRoleService;
import com.gijela.morpheus.pistil.service.ISysUserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gijela.morpheus.pistil.event.PermissionChangePublisher;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {

    private static final Logger logger = LoggerFactory.getLogger(SysRoleServiceImpl.class);

    @Autowired
    private ISysRoleMenuService roleMenuService;
    @Autowired
    private ISysUserRoleService userRoleService;
    @Autowired
    private PermissionChangePublisher permissionChangePublisher;

    // 配置项留存以备后续使用（当前逻辑由事件监听器处理）
    @Value("${security.cache.user.key-prefix:security:user:}")
    private String userCacheKeyPrefix;
    @Value("${security.cache.user.ttl-minutes:10}")
    private long userCacheTtlMinutes;

    @Override
    public Page<RoleVO> pageRoles(RolePageDTO dto) {
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysRole.class).contains(sf)) {
            sf = null;
        }
        Page<SysRole> page = PageSortUtil.buildPage(dto.getCurrent(), dto.getSize(), sf, dto.getSortOrder(), SysRole.class, "create_time");
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        page = this.page(page, wrapper);
        Page<RoleVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(r -> {
            RoleVO vo = new RoleVO();
            vo.setId(r.getId());
            vo.setName(r.getName());
            vo.setCode(r.getCode());
            vo.setSort(r.getSort());
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Long saveRole(SaveRoleDTO dto) {
        boolean create = dto.getId() == null;
        // 名称/编码唯一
        LambdaQueryWrapper<SysRole> nameQ = Wrappers.<SysRole>lambdaQuery().eq(SysRole::getName, dto.getName());
        if (!create) nameQ.ne(SysRole::getId, dto.getId());
        if (this.count(nameQ) > 0) throw new BizException(ErrorCode.CONFLICT, "角色名称已存在");
        LambdaQueryWrapper<SysRole> codeQ = Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, dto.getCode());
        if (!create) codeQ.ne(SysRole::getId, dto.getId());
        if (this.count(codeQ) > 0) throw new BizException(ErrorCode.CONFLICT, "角色编码已存在");
        SysRole role = create ? new SysRole() : this.getById(dto.getId());
        if (!create && role == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setSort(dto.getSort());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        this.saveOrUpdate(role);
        return role.getId();
    }

    @Override
    public void deleteRoles(IdsDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getIds())) return;
        // 被用户引用
        long userRef = userRoleService.count(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getRoleId, dto.getIds()));
        if (userRef > 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "存在用户引用，禁止删除");
        // 直接删角色及角色菜单关联
        this.removeBatchByIds(dto.getIds());
        roleMenuService.remove(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, dto.getIds()));
    }

    @Override
    @Transactional
    public void assignPerms(RolePermsDTO dto) {
        if (dto == null || dto.getRoleId() == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "roleId 不能为空");
        SysRole r = this.getById(dto.getRoleId());
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        // 清空旧
        roleMenuService.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, dto.getRoleId()));
        if (!CollectionUtils.isEmpty(dto.getMenuIds())) {
            List<SysRoleMenu> list = dto.getMenuIds().stream().filter(Objects::nonNull).distinct().map(mid -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(dto.getRoleId());
                rm.setMenuId(mid);
                return rm;
            }).collect(Collectors.toList());
            if (!list.isEmpty()) roleMenuService.saveBatch(list);
        }

        // 发布角色权限变更事件，由 listener 在事务提交后异步处理权限聚合与 tokenVersion 更新
        try {
            permissionChangePublisher.publishRolePermissionChanged(Collections.singleton(dto.getRoleId()));
        } catch (Exception ex) {
            logger.warn("Failed to publish RolePermissionChangedEvent for roleId={}: {}", dto.getRoleId(), ex.toString());
        }
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        if (roleId == null) return List.of();
        return roleMenuService.list(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    public RoleVO getRoleDetail(Long id) {
        if (id == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysRole r = this.getById(id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        RoleVO vo = new RoleVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCode(r.getCode());
        vo.setSort(r.getSort());
        vo.setStatus(r.getStatus());
        vo.setRemark(r.getRemark());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    @Override
    public void changeStatus(ChangeStatusDTO dto) {
        if (dto == null || dto.getId() == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysRole r = this.getById(dto.getId());
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        r.setStatus(dto.getStatus());
        this.updateById(r);
    }
}

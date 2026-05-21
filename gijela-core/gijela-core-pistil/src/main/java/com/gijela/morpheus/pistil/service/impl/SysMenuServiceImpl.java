package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.SaveMenuDTO;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.entity.SysRoleMenu;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysMenuMapper;
import com.gijela.morpheus.pistil.service.ISysMenuService;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.service.ISysRoleMenuService;
import com.gijela.morpheus.pistil.service.ISysUserRoleService;
import java.util.stream.Collectors;

import com.gijela.morpheus.pistil.service.ISysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 菜单/权限表 服务实现类
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    @Autowired
    private ISysUserRoleService userRoleService;
    @Autowired
    private ISysRoleMenuService roleMenuService;
    @Autowired
    private ISysRoleService roleService;

    @Override
    public List<MenuTreeVO> buildFullTree() {
        List<SysMenu> all = this.list(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        return buildTreeFromList(all);
    }

    @Override
    public List<MenuTreeVO> buildMenuTreeForUser(Long userId) {
        // 获取用户角色 ID
        List<Long> roleIds = userRoleService
                .list(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) return Collections.emptyList();
        // SYS_ADMIN 返回全量树
        List<SysRole> roles = roleService.list(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds));
        if (roles.stream().anyMatch(r -> "SYS_ADMIN".equals(r.getCode()))) {
            return buildFullTree();
        }
        // 查询角色关联菜单 ID
        List<Long> menuIds = roleMenuService
                .list(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        if (menuIds.isEmpty()) return Collections.emptyList();
        // 查询启用菜单
        List<SysMenu> menus = new ArrayList<>(this.list(Wrappers.<SysMenu>lambdaQuery()
                .in(SysMenu::getId, menuIds).eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId)));
        if (menus.isEmpty()) return Collections.emptyList();
        // 补全父节点，保证菜单树结构完整
        Set<Long> existIds = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        List<SysMenu> extras = new ArrayList<>();
        for (SysMenu m : new ArrayList<>(menus)) {
            Long pid = m.getParentId();
            while (pid != null && pid != 0L && !existIds.contains(pid)) {
                SysMenu parent = this.getById(pid);
                if (parent == null) break;
                extras.add(parent);
                existIds.add(parent.getId());
                pid = parent.getParentId();
            }
        }
        if (!extras.isEmpty()) {
            menus.addAll(extras);
            menus.sort(Comparator.comparing((SysMenu s) -> Optional.ofNullable(s.getSort()).orElse(0))
                    .thenComparing(SysMenu::getId));
        }
        return buildTreeFromList(menus);
    }

    /** 将菜单列表转换为树结构（调用前已保证顺序） */
    private List<MenuTreeVO> buildTreeFromList(List<SysMenu> menus) {
        if (CollectionUtils.isEmpty(menus)) return Collections.emptyList();
        Map<Long, MenuTreeVO> map = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setId(m.getId());
            vo.setParentId(m.getParentId());
            vo.setName(m.getName());
            vo.setType(m.getType());
            vo.setPath(m.getPath());
            vo.setPermission(m.getPermission());
            vo.setIcon(m.getIcon());
            vo.setSort(m.getSort());
            vo.setStatus(m.getStatus());
            map.put(vo.getId(), vo);
        }
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO vo : map.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuTreeVO> list) {
        if (list == null) return;
        list.sort(Comparator.comparing((MenuTreeVO m) -> Optional.ofNullable(m.getSort()).orElse(0))
                .thenComparing(MenuTreeVO::getId));
        for (MenuTreeVO m : list) {
            sortTree(m.getChildren());
        }
    }

    @Override
    public Long saveOrUpdate(SaveMenuDTO dto) {
        // 基础合法性
        if (dto.getParentId() != null && dto.getId() != null && dto.getParentId().equals(dto.getId())) {
            throw new BizException(ErrorCode.CONFLICT, "不能将自身设为父节点");
        }
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId > 0) {
            SysMenu parent = this.getById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "父菜单不存在");
            }
            if ("B".equals(parent.getType())) {
                throw new BizException(ErrorCode.CONFLICT, "按钮类型不能再挂子节点");
            }
        }
        // 同级名称唯一
        LambdaQueryWrapper<SysMenu> nameQ = Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, parentId)
                .eq(SysMenu::getName, dto.getName());
        if (dto.getId() != null) nameQ.ne(SysMenu::getId, dto.getId());
        if (this.count(nameQ) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "同级名称已存在");
        }
        // 权限标识唯一（允许为空）
        if (StringUtils.hasText(dto.getPermission())) {
            LambdaQueryWrapper<SysMenu> permQ = Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getPermission, dto.getPermission());
            if (dto.getId() != null) permQ.ne(SysMenu::getId, dto.getId());
            if (this.count(permQ) > 0) {
                throw new BizException(ErrorCode.CONFLICT, "权限标识已存在");
            }
        }
        SysMenu entity = dto.getId() == null ? new SysMenu() : this.getById(dto.getId());
        if (dto.getId() != null && entity == null) throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        entity.setParentId(parentId);
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setPath(dto.getPath());
        entity.setPermission(dto.getPermission());
        entity.setIcon(dto.getIcon());
        entity.setSort(dto.getSort());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        this.saveOrUpdate(entity);
        return entity.getId();
    }

    @Override
    public void deleteMenus(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) return;
        // 子节点校验
        long childCount = this.count(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getParentId, ids));
        if (childCount > 0) {
            throw new BizException(ErrorCode.ILLEGAL_STATE, "存在子菜单，禁止删除");
        }
        // 角色引用校验
        List<SysRoleMenu> refs = roleMenuService.list(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getMenuId, ids));
        if (!CollectionUtils.isEmpty(refs)) {
            // 收集引用的角色 id 并查询角色信息
            List<Long> roleIds = refs.stream().map(SysRoleMenu::getRoleId).distinct().collect(Collectors.toList());
            List<SysRole> roles = roleService.listByIds(roleIds);
            String roleInfo = roles.stream()
                    .map(r -> (r.getId() == null ? "" : r.getId().toString())
                            + "(" + (r.getCode() == null ? "" : r.getCode())
                            + "," + (r.getName() == null ? "" : r.getName()) + ")")
                    .collect(Collectors.joining(", "));
            throw new BizException(ErrorCode.ILLEGAL_STATE, "存在角色引用，禁止删除。引用角色: " + roleInfo);
        }
        this.removeBatchByIds(ids);
    }

    @Override
    public SysMenu getMenuDetail(Long id) {
        if (id == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysMenu m = this.getById(id);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        return m;
    }

    @Override
    public void changeStatus(ChangeStatusDTO dto) {
        if (dto == null || dto.getId() == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysMenu m = this.getById(dto.getId());
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        m.setStatus(dto.getStatus());
        this.updateById(m);
    }
}

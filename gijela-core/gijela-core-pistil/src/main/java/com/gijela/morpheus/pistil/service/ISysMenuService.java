package com.gijela.morpheus.pistil.service;

import com.gijela.morpheus.pistil.domain.dto.SaveMenuDTO;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;

import java.util.List;

/**
 * <p>
 * 菜单/权限表 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysMenuService extends IService<SysMenu> {
    /** 构建全部菜单树 */
    List<MenuTreeVO> buildFullTree();
    /** 根据用户 ID 构建该用户有权限的菜单树（SYS_ADMIN 返回全量树） */
    List<MenuTreeVO> buildMenuTreeForUser(Long userId);
    /** 新增或编辑菜单 */
    Long saveOrUpdate(SaveMenuDTO dto);
    /** 批量删除（校验子节点与角色引用） */
    void deleteMenus(List<Long> ids);
    /** 获取菜单详情 */
    SysMenu getMenuDetail(Long id);
    void changeStatus(ChangeStatusDTO dto);
}

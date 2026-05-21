package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.entity.SysRole;
import com.gijela.morpheus.pistil.domain.vo.RoleVO;

import java.util.List;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysRoleService extends IService<SysRole> {
    Page<RoleVO> pageRoles(RolePageDTO dto);
    Long saveRole(SaveRoleDTO dto);
    void deleteRoles(IdsDTO dto);
    void assignPerms(RolePermsDTO dto);
    List<Long> getRoleMenuIds(Long roleId);
    RoleVO getRoleDetail(Long id);
    void changeStatus(ChangeStatusDTO dto);
}

package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.entity.SysUserDept;

import java.util.List;

/**
 * <p>
 * 用户-部门关联 服务类
 * </p>
 */
public interface ISysUserDeptService extends IService<SysUserDept> {

    /**
     * 设置用户的部门关联（先删除旧的，再插入新的）
     */
    void assignUserDepts(Long userId, List<Long> deptIds);

    /**
     * 查询用户关联的部门ID列表
     */
    List<Long> listDeptIdsByUser(Long userId);

}

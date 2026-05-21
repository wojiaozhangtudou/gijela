package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.pistil.domain.entity.SysUserDept;
import com.gijela.morpheus.pistil.mapper.SysUserDeptMapper;
import com.gijela.morpheus.pistil.service.ISysUserDeptService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户-部门关联 服务实现类
 * </p>
 */
@Service
public class SysUserDeptServiceImpl extends ServiceImpl<SysUserDeptMapper, SysUserDept> implements ISysUserDeptService {

    @Override
    public void assignUserDepts(Long userId, List<Long> deptIds) {
        if (userId == null) return;
        // 删除旧的
        this.remove(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, userId));
        if (CollectionUtils.isEmpty(deptIds)) return;
        List<SysUserDept> list = deptIds.stream().distinct().map(did -> {
            SysUserDept ud = new SysUserDept();
            ud.setUserId(userId);
            ud.setDeptId(did);
            return ud;
        }).collect(Collectors.toList());
        this.saveBatch(list);
    }

    @Override
    public List<Long> listDeptIdsByUser(Long userId) {
        if (userId == null) return List.of();
        return this.list(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, userId))
                .stream().map(SysUserDept::getDeptId).collect(Collectors.toList());
    }
}


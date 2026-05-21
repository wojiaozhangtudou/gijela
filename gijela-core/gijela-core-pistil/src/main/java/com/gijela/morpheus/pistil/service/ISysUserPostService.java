package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.entity.SysUserPost;

import java.util.List;

/**
 * <p>
 * 用户-岗位关联 服务类
 * </p>
 */
public interface ISysUserPostService extends IService<SysUserPost> {

    /**
     * 设置用户的岗位关联（先删除旧的，再插入新的）
     */
    void assignUserPosts(Long userId, List<Long> postIds);

    /**
     * 查询用户关联的岗位ID列表
     */
    List<Long> listPostIdsByUser(Long userId);

}

package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.pistil.domain.entity.SysUserPost;
import com.gijela.morpheus.pistil.mapper.SysUserPostMapper;
import com.gijela.morpheus.pistil.service.ISysUserPostService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户-岗位关联 服务实现类
 * </p>
 */
@Service
public class SysUserPostServiceImpl extends ServiceImpl<SysUserPostMapper, SysUserPost> implements ISysUserPostService {

    @Override
    public void assignUserPosts(Long userId, List<Long> postIds) {
        if (userId == null) return;
        this.remove(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userId));
        if (CollectionUtils.isEmpty(postIds)) return;
        List<SysUserPost> list = postIds.stream().distinct().map(pid -> {
            SysUserPost up = new SysUserPost();
            up.setUserId(userId);
            up.setPostId(pid);
            return up;
        }).collect(Collectors.toList());
        this.saveBatch(list);
    }

    @Override
    public List<Long> listPostIdsByUser(Long userId) {
        if (userId == null) return List.of();
        return this.list(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userId))
                .stream().map(SysUserPost::getPostId).collect(Collectors.toList());
    }
}


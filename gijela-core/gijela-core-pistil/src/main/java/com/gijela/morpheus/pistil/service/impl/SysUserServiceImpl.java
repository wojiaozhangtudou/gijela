package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.common.BizException;
import com.gijela.morpheus.pistil.support.query.QueryWrapperBuilder;
import com.gijela.morpheus.pistil.support.sort.PageSortUtil;
import com.gijela.morpheus.pistil.support.sort.SortableFieldResolver;
import com.gijela.morpheus.pistil.domain.dto.*;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.entity.SysUserRole;
import com.gijela.morpheus.pistil.domain.vo.UserVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysUserMapper;
import com.gijela.morpheus.pistil.service.ISysUserRoleService;
import com.gijela.morpheus.pistil.service.ISysUserDeptService;
import com.gijela.morpheus.pistil.service.ISysUserPostService;
import com.gijela.morpheus.pistil.service.ISysUserService;
import com.gijela.morpheus.pistil.domain.entity.SysUserDept;
import com.gijela.morpheus.pistil.domain.entity.SysUserPost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=]*$");
    private static final int AVATAR_MAX_LEN = 60000;

    @Autowired
    private ISysUserRoleService userRoleService;
    @Autowired
    private ISysUserDeptService userDeptService;
    @Autowired
    private ISysUserPostService userPostService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Page<UserVO> pageUsers(UserPageDTO dto) {
        // 校验排序字段
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysUser.class).contains(sf)) {
            sf = null; // 非法忽略
        }
        Page<SysUser> page = PageSortUtil.buildPage(dto.getCurrent(), dto.getSize(), sf, dto.getSortOrder(), SysUser.class, "create_time");
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        page = this.page(page, wrapper);
        List<SysUser> records = page.getRecords();
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (CollectionUtils.isEmpty(records)) { voPage.setRecords(Collections.emptyList()); return voPage; }
        List<Long> userIds = records.stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, List<Long>> userRoles = userRoleService
                .list(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        Map<Long, List<Long>> userDepts = userDeptService
                .list(Wrappers.<SysUserDept>lambdaQuery().in(SysUserDept::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(SysUserDept::getUserId,
                        Collectors.mapping(SysUserDept::getDeptId, Collectors.toList())));
        Map<Long, List<Long>> userPosts = userPostService
                .list(Wrappers.<SysUserPost>lambdaQuery().in(SysUserPost::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(SysUserPost::getUserId,
                        Collectors.mapping(SysUserPost::getPostId, Collectors.toList())));
        List<UserVO> voList = records.stream().map(u -> {
            UserVO v = new UserVO();
            v.setId(u.getId());
            v.setUsername(u.getUsername());
            v.setNickname(u.getNickname());
            v.setEmail(u.getEmail());
            v.setPhone(u.getPhone());
            v.setStatus(u.getStatus());
            v.setDeptId(u.getDeptId());
            v.setPostId(u.getPostId());
            v.setDeptIds(userDepts.getOrDefault(u.getId(), Collections.emptyList()));
            v.setPostIds(userPosts.getOrDefault(u.getId(), Collections.emptyList()));
            v.setCreateTime(u.getCreateTime());
            v.setRoleIds(userRoles.getOrDefault(u.getId(), Collections.emptyList()));
            return v;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Long saveUser(SaveUserDTO dto) {
        boolean isCreate = dto.getId() == null;
        // 唯一性：username
        LambdaQueryWrapper<SysUser> uq = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername());
        if (!isCreate) uq.ne(SysUser::getId, dto.getId());
        if (this.count(uq) > 0) throw new BizException(ErrorCode.CONFLICT, "用户名已存在");
        SysUser entity = isCreate ? new SysUser() : this.getById(dto.getId());
        if (!isCreate && entity == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        entity.setUsername(dto.getUsername());
        entity.setNickname(dto.getNickname());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setStatus(dto.getStatus());
        // 保持单列兼容字段
        entity.setDeptId(dto.getDeptId());
        entity.setPostId(dto.getPostId());
        if (isCreate) {
            if (!StringUtils.hasText(dto.getPassword())) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, "新增用户密码不能为空");
            }
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else if (StringUtils.hasText(dto.getPassword())) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getAvatarBase64() != null) {
            validateAvatar(dto.getAvatarBase64());
            entity.setAvatarBase64(dto.getAvatarBase64());
        }
        this.saveOrUpdate(entity);
        // 清除 Redis 中缓存的用户详情（如果存在）
        evictUserCacheByUsername(entity.getUsername());
        // 角色关联
        if (dto.getRoleIds() != null) {
            // 先删后插
            userRoleService.remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, entity.getId()));
            if (!dto.getRoleIds().isEmpty()) {
                List<SysUserRole> list = dto.getRoleIds().stream().distinct().map(rid -> {
                    SysUserRole ur = new SysUserRole();
                    ur.setUserId(entity.getId());
                    ur.setRoleId(rid);
                    return ur;
                }).collect(Collectors.toList());
                userRoleService.saveBatch(list);
            }
        }
        // 部门关联（优先使用 deptIds；若 absent 但 deptId 有值则使用单值）
        if (dto.getDeptIds() != null) {
            userDeptService.assignUserDepts(entity.getId(), dto.getDeptIds());
        } else if (dto.getDeptId() != null) {
            List<Long> deptIds = dto.getDeptId() == null ? List.of() : List.of(dto.getDeptId());
            userDeptService.assignUserDepts(entity.getId(), deptIds);
        }
        // 岗位关联（优先使用 postIds；若 absent 但 postId 有值则使用单值）
        if (dto.getPostIds() != null) {
            userPostService.assignUserPosts(entity.getId(), dto.getPostIds());
        } else if (dto.getPostId() != null) {
            List<Long> postIds = dto.getPostId() == null ? List.of() : List.of(dto.getPostId());
            userPostService.assignUserPosts(entity.getId(), postIds);
        }
        return entity.getId();
    }

    private void validateAvatar(String avatarBase64) {
        if (avatarBase64 == null) return;
        if (avatarBase64.length() > AVATAR_MAX_LEN) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "头像过大");
        }
        if (!BASE64_PATTERN.matcher(avatarBase64).matches()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "头像Base64格式非法");
        }
    }

    @Override
    public void updateAvatar(UpdateAvatarDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "userId不能为空");
        }
        SysUser u = this.getById(dto.getUserId());
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        if (dto.getAvatarBase64() != null) {
            validateAvatar(dto.getAvatarBase64());
        }
        u.setAvatarBase64(dto.getAvatarBase64()); // 允许置空
        this.updateById(u);
        evictUserCacheByUsername(u.getUsername());
    }

    @Override
    public String getAvatar(Long userId) {
        if (userId == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "userId不能为空");
        SysUser u = this.getById(userId);
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        return u.getAvatarBase64();
    }

    @Override
    public void deleteUsers(IdsDTO dto) {
        if (CollectionUtils.isEmpty(dto.getIds())) return;
        // 先读出要删除的用户名以便清除缓存
        List<SysUser> toDelete = this.listByIds(dto.getIds());
        for (SysUser su : toDelete) {
            if (su != null && su.getUsername() != null) evictUserCacheByUsername(su.getUsername());
        }
        this.removeBatchByIds(dto.getIds());
        userRoleService.remove(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, dto.getIds()));
        userDeptService.remove(Wrappers.<SysUserDept>lambdaQuery().in(SysUserDept::getUserId, dto.getIds()));
        userPostService.remove(Wrappers.<SysUserPost>lambdaQuery().in(SysUserPost::getUserId, dto.getIds()));
    }

    @Override
    public void changeStatus(ChangeStatusDTO dto) {
        SysUser u = this.getById(dto.getId());
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        u.setStatus(dto.getStatus());
        this.updateById(u);
        evictUserCacheByUsername(u.getUsername());
    }

    @Override
    public void resetPassword(ResetPwdDTO dto) {
        SysUser u = this.getById(dto.getId());
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        this.updateById(u);
        evictUserCacheByUsername(u.getUsername());
    }

    private void evictUserCacheByUsername(String username) {
        if (username == null) return;
        try {
            redisTemplate.delete("security:user:" + username);
        } catch (Exception e) {
            // 忽略缓存删除失败
        }
    }

    @Override
    public UserVO getUserDetail(Long id) {
        if (id == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysUser u = this.getById(id);
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        List<Long> roleIds = userRoleService.list(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<Long> deptIds = userDeptService.list(Wrappers.<SysUserDept>lambdaQuery().eq(SysUserDept::getUserId, id))
                .stream().map(SysUserDept::getDeptId).collect(Collectors.toList());
        List<Long> postIds = userPostService.list(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, id))
                .stream().map(SysUserPost::getPostId).collect(Collectors.toList());
        UserVO v = new UserVO();
        v.setId(u.getId());
        v.setUsername(u.getUsername());
        v.setNickname(u.getNickname());
        v.setEmail(u.getEmail());
        v.setPhone(u.getPhone());
        v.setStatus(u.getStatus());
        v.setDeptId(u.getDeptId());
        v.setPostId(u.getPostId());
        v.setDeptIds(deptIds);
        v.setPostIds(postIds);
        v.setCreateTime(u.getCreateTime());
        v.setRoleIds(roleIds);
        v.setAvatarBase64(u.getAvatarBase64());
        return v;
    }
}

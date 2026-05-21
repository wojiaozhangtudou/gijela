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
import com.gijela.morpheus.pistil.domain.dto.PostPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SavePostDTO;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.entity.SysPost;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.vo.PostVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysPostMapper;
import com.gijela.morpheus.pistil.service.ISysPostService;
import com.gijela.morpheus.pistil.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/** 岗位表 服务实现类 */
@Service
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements ISysPostService {

    @Autowired
    private ISysUserService userService;

    @Override
    public Page<PostVO> pagePosts(PostPageDTO dto) {
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysPost.class).contains(sf)) {
            sf = null;
        }
        // 岗位表无 create_time 字段，fallback 使用 id
        Page<SysPost> page = PageSortUtil.buildPage(dto.getCurrent(), dto.getSize(), sf, dto.getSortOrder(), SysPost.class, "id");
        QueryWrapper<SysPost> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        page = this.page(page, wrapper);
        Page<PostVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(p -> {
            PostVO v = new PostVO();
            v.setId(p.getId());
            v.setName(p.getName());
            v.setCode(p.getCode());
            v.setSort(p.getSort());
            v.setStatus(p.getStatus());
            v.setRemark(p.getRemark());
            return v;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Long savePost(SavePostDTO dto) {
        boolean create = dto.getId() == null;
        // 名称唯一
        LambdaQueryWrapper<SysPost> nameQ = Wrappers.<SysPost>lambdaQuery().eq(SysPost::getName, dto.getName());
        if (!create) nameQ.ne(SysPost::getId, dto.getId());
        if (this.count(nameQ) > 0) throw new BizException(ErrorCode.CONFLICT, "岗位名称已存在");
        // 编码唯一
        LambdaQueryWrapper<SysPost> codeQ = Wrappers.<SysPost>lambdaQuery().eq(SysPost::getCode, dto.getCode());
        if (!create) codeQ.ne(SysPost::getId, dto.getId());
        if (this.count(codeQ) > 0) throw new BizException(ErrorCode.CONFLICT, "岗位编码已存在");
        SysPost entity = create ? new SysPost() : this.getById(dto.getId());
        if (!create && entity == null) throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setSort(dto.getSort());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        this.saveOrUpdate(entity);
        return entity.getId();
    }

    @Override
    public void deletePosts(IdsDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getIds())) return;
        long userRef = userService.count(Wrappers.<SysUser>lambdaQuery().in(SysUser::getPostId, dto.getIds()));
        if (userRef > 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "存在用户引用，禁止删除");
        this.removeBatchByIds(dto.getIds());
    }

    @Override
    public PostVO getPostDetail(Long id) {
        if (id == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysPost p = this.getById(id);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        PostVO v = new PostVO();
        v.setId(p.getId());
        v.setName(p.getName());
        v.setCode(p.getCode());
        v.setSort(p.getSort());
        v.setStatus(p.getStatus());
        v.setRemark(p.getRemark());
        return v;
    }

    @Override
    public void changeStatus(ChangeStatusDTO dto) {
        if (dto == null || dto.getId() == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysPost p = this.getById(dto.getId());
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        p.setStatus(dto.getStatus());
        this.updateById(p);
    }
}

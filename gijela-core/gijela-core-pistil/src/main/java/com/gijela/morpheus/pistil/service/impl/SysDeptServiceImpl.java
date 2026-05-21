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
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.DeptPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SaveDeptDTO;
import com.gijela.morpheus.pistil.domain.entity.SysDept;
import com.gijela.morpheus.pistil.domain.entity.SysUser;
import com.gijela.morpheus.pistil.domain.vo.DeptVO;
import com.gijela.morpheus.common.enums.ErrorCode;
import com.gijela.morpheus.pistil.mapper.SysDeptMapper;
import com.gijela.morpheus.pistil.service.ISysDeptService;
import com.gijela.morpheus.pistil.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;

/** 部门表 服务实现类 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {

    @Autowired
    private ISysUserService userService;

    @Override
    public Page<DeptVO> pageDepts(DeptPageDTO dto) {
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysDept.class).contains(sf)) {
            sf = null;
        }
        Page<SysDept> page = PageSortUtil.buildPage(dto.getCurrent(), dto.getSize(), sf, dto.getSortOrder(), SysDept.class, "create_time");
        QueryWrapper<SysDept> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        page = this.page(page, wrapper);
        Page<DeptVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(d -> {
            DeptVO v = new DeptVO();
            v.setId(d.getId());
            v.setParentId(d.getParentId());
            v.setName(d.getName());
            v.setLeader(d.getLeader());
            v.setPhone(d.getPhone());
            v.setEmail(d.getEmail());
            v.setSort(d.getSort());
            v.setStatus(d.getStatus());
            v.setCreateTime(d.getCreateTime());
            return v;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Long saveDept(SaveDeptDTO dto) {
        boolean create = dto.getId() == null;
        Long pid = dto.getParentId() == null ? 0L : dto.getParentId();
        if (!create && Objects.equals(dto.getId(), pid)) {
            throw new BizException(ErrorCode.CONFLICT, "不能将自身作为父节点");
        }
        if (pid < 0) pid = 0L;
        // 父存在性（0 根除外）
        if (pid > 0 && this.getById(pid) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "父部门不存在");
        }
        // 同父下名称唯一
        LambdaQueryWrapper<SysDept> nameQ = Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getParentId, pid)
                .eq(SysDept::getName, dto.getName());
        if (!create) nameQ.ne(SysDept::getId, dto.getId());
        if (this.count(nameQ) > 0) throw new BizException(ErrorCode.CONFLICT, "同级部门名称已存在");
        SysDept entity = create ? new SysDept() : this.getById(dto.getId());
        if (!create && entity == null) throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        entity.setParentId(pid);
        entity.setName(dto.getName());
        entity.setLeader(dto.getLeader());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setSort(dto.getSort());
        entity.setStatus(dto.getStatus());
        this.saveOrUpdate(entity);
        return entity.getId();
    }

    @Override
    public void deleteDepts(IdsDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getIds())) return;
        // 子部门
        long child = this.count(Wrappers.<SysDept>lambdaQuery().in(SysDept::getParentId, dto.getIds()));
        if (child > 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "存在子部门，禁止删除");
        // ���户引用
        long userRef = userService.count(Wrappers.<SysUser>lambdaQuery().in(SysUser::getDeptId, dto.getIds()));
        if (userRef > 0) throw new BizException(ErrorCode.ILLEGAL_STATE, "存在用户引用，禁止删除");
        this.removeBatchByIds(dto.getIds());
    }

    @Override
    public DeptVO getDeptDetail(Long id) {
        if (id == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysDept d = this.getById(id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        DeptVO v = new DeptVO();
        v.setId(d.getId());
        v.setParentId(d.getParentId());
        v.setName(d.getName());
        v.setLeader(d.getLeader());
        v.setPhone(d.getPhone());
        v.setEmail(d.getEmail());
        v.setSort(d.getSort());
        v.setStatus(d.getStatus());
        v.setCreateTime(d.getCreateTime());
        return v;
    }

    @Override
    public void changeStatus(ChangeStatusDTO dto) {
        if (dto == null || dto.getId() == null) throw new BizException(ErrorCode.INVALID_ARGUMENT, "ID 不能为空");
        SysDept d = this.getById(dto.getId());
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        d.setStatus(dto.getStatus());
        this.updateById(d);
    }

    @Override
    public List<DeptVO> getDeptTree() {
        List<SysDept> list = this.list();
        List<DeptVO> vos = list.stream().map(d -> {
            DeptVO v = new DeptVO();
            v.setId(d.getId());
            v.setParentId(d.getParentId());
            v.setName(d.getName());
            v.setLeader(d.getLeader());
            v.setPhone(d.getPhone());
            v.setEmail(d.getEmail());
            v.setSort(d.getSort());
            v.setStatus(d.getStatus());
            v.setCreateTime(d.getCreateTime());
            return v;
        }).collect(Collectors.toList());
        return buildTree(vos);
    }

    @Override
    public List<DeptVO> getDeptSelectTree() {
        List<SysDept> list = this.list();
        List<DeptVO> vos = list.stream().map(d -> {
            DeptVO v = new DeptVO();
            v.setId(d.getId());
            v.setParentId(d.getParentId());
            v.setName(d.getName());
            v.setSort(d.getSort());
            return v;
        }).collect(Collectors.toList());
        return buildTree(vos);
    }

    /**
     * 将扁平列表组装成树形结构（按 sort 升序）
     */
    private List<DeptVO> buildTree(List<DeptVO> list) {
        Map<Long, DeptVO> map = new HashMap<>();
        for (DeptVO v : list) {
            map.put(v.getId(), v);
        }
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO v : list) {
            Long pid = v.getParentId();
            if (pid == null || pid == 0L || map.get(pid) == null) {
                roots.add(v);
            } else {
                DeptVO parent = map.get(pid);
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(v);
            }
        }
        // 排序
        Comparator<DeptVO> cmp = Comparator.comparing(o -> o.getSort() == null ? 0 : o.getSort());
        roots.sort(cmp);
        // 对子树递归排序
        sortChildrenRecursive(roots, cmp);
        return roots;
    }

    private void sortChildrenRecursive(List<DeptVO> nodes, Comparator<DeptVO> cmp) {
        if (nodes == null) return;
        for (DeptVO n : nodes) {
            List<DeptVO> ch = n.getChildren();
            if (ch != null) {
                ch.sort(cmp);
                sortChildrenRecursive(ch, cmp);
            }
        }
    }
}

package com.gijela.morpheus.pistil.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gijela.morpheus.pistil.support.query.QueryWrapperBuilder;
import com.gijela.morpheus.pistil.support.sort.PageSortUtil;
import com.gijela.morpheus.pistil.support.sort.SortableFieldResolver;
import com.gijela.morpheus.pistil.domain.dto.AuditLogPageDTO;
import com.gijela.morpheus.pistil.domain.entity.SysAuditLog;
import com.gijela.morpheus.pistil.domain.vo.AuditLogVO;
import com.gijela.morpheus.pistil.mapper.SysAuditLogMapper;
import com.gijela.morpheus.pistil.service.ISysAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * <p>
 * 审计日志 服务实现类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
@Service
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog> implements ISysAuditLogService {

    @Override
    public Page<AuditLogVO> pageLogs(AuditLogPageDTO dto) {
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysAuditLog.class).contains(sf)) {
            sf = null; // 非法忽略
        }
        // fallback: id DESC（需求表）
        Page<SysAuditLog> page = PageSortUtil.buildPage(dto.getCurrent(), dto.getSize(), sf, dto.getSortOrder(), SysAuditLog.class, "id");
        QueryWrapper<SysAuditLog> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        page = this.page(page, wrapper);
        Page<AuditLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(e -> {
            AuditLogVO vo = new AuditLogVO();
            vo.setId(e.getId());
            vo.setUserId(e.getUserId());
            vo.setUsername(e.getUsername());
            vo.setOperation(e.getOperation());
            vo.setMethod(e.getMethod());
            vo.setIp(e.getIp());
            vo.setUserAgent(e.getUserAgent());
            vo.setCreateTime(e.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }
}

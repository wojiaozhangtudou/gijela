package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.dto.AuditLogPageDTO;
import com.gijela.morpheus.pistil.domain.entity.SysAuditLog;
import com.gijela.morpheus.pistil.domain.vo.AuditLogVO;

/**
 * <p>
 * 审计日志 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysAuditLogService extends IService<SysAuditLog> {
    Page<AuditLogVO> pageLogs(AuditLogPageDTO dto);
}

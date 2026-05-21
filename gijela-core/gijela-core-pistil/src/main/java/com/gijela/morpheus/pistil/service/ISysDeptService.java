package com.gijela.morpheus.pistil.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.DeptPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SaveDeptDTO;
import com.gijela.morpheus.pistil.domain.entity.SysDept;
import com.gijela.morpheus.pistil.domain.vo.DeptVO;
import java.util.List;

/**
 * <p>
 * 部门表 服务类
 * </p>
 *
 * @author wojiaozhangtudou
 * @since 2025-08-21
 */
public interface ISysDeptService extends IService<SysDept> {

    Page<DeptVO> pageDepts(DeptPageDTO dto);

    Long saveDept(SaveDeptDTO dto);

    void deleteDepts(IdsDTO dto);

    DeptVO getDeptDetail(Long id);

    void changeStatus(ChangeStatusDTO dto);

    /**
     * 返回完整的部门树（含所有字段）
     */
    List<DeptVO> getDeptTree();

    /**
     * 返回用于下拉选择的部门树（可只包含 id/name/children）
     */
    List<DeptVO> getDeptSelectTree();
}

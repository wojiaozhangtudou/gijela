package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.DeptPageDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.SaveDeptDTO;
import com.gijela.morpheus.pistil.domain.vo.DeptVO;
import com.gijela.morpheus.pistil.service.ISysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/depts")
@Tag(name = "部门管理")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class DeptController {

    @Autowired
    private ISysDeptService deptService;

    @PostMapping("/page")
    @Operation(summary = "部门分页查询")
    public ApiResponse<Page<DeptVO>> page(@Valid @RequestBody DeptPageDTO dto) {
        return ApiResponse.ok(deptService.pageDepts(dto));
    }

    @PostMapping
    @Operation(summary = "新增或编辑部门")
    public ApiResponse<Long> save(@Valid @RequestBody SaveDeptDTO dto) {
        return ApiResponse.ok(deptService.saveDept(dto));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除部门")
    public ApiResponse<Void> delete(@Valid @RequestBody IdsDTO dto) {
        deptService.deleteDepts(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/status")
    @Operation(summary = "修改部门状态")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        deptService.changeStatus(dto);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tree")
    @Operation(summary = "部门树（完整）")
    public ApiResponse<List<DeptVO>> tree() {
        return ApiResponse.ok(deptService.getDeptTree());
    }

    @GetMapping("/select-tree")
    @Operation(summary = "部门树（下拉用，精简字段）")
    public ApiResponse<List<DeptVO>> selectTree() {
        return ApiResponse.ok(deptService.getDeptSelectTree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "部门详情")
    public ApiResponse<DeptVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(deptService.getDeptDetail(id));
    }
}

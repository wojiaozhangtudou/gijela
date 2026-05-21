package com.gijela.morpheus.pistil.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gijela.morpheus.common.ApiResponse;
import com.gijela.morpheus.pistil.support.query.QueryWrapperBuilder;
import com.gijela.morpheus.pistil.support.sort.PageSortUtil;
import com.gijela.morpheus.pistil.support.sort.SortableFieldResolver;
import com.gijela.morpheus.pistil.domain.dto.ChangeStatusDTO;
import com.gijela.morpheus.pistil.domain.dto.IdsDTO;
import com.gijela.morpheus.pistil.domain.dto.MenuPageDTO;
import com.gijela.morpheus.pistil.domain.dto.SaveMenuDTO;
import com.gijela.morpheus.pistil.domain.entity.SysMenu;
import com.gijela.morpheus.pistil.domain.vo.MenuTreeVO;
import com.gijela.morpheus.pistil.service.ISysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menus")
@Tag(name = "菜单管理")
@SecurityRequirement(name = "bearer-jwt")
@Validated
public class MenuController {

    @Autowired
    private ISysMenuService menuService;

    @PostMapping("/page")
    @Operation(summary = "菜单分页查询")
    public ApiResponse<Page<SysMenu>> page(@Valid @RequestBody MenuPageDTO dto) {
        QueryWrapper<SysMenu> wrapper = new QueryWrapper<>();
        QueryWrapperBuilder.apply(dto, wrapper);
        // 校验排序字段是否在 @Sortable 列表内（若提供）
        String sf = dto.getSortField();
        if (StringUtils.hasText(sf) && !SortableFieldResolver.getSortableFields(SysMenu.class).contains(sf)) {
            // 非法忽略并 fallback sort ASC, id DESC（sort ASC 通过自定义双列：先 sort ASC 再 id DESC）
            sf = null; // 强制走 fallback
        }
        Page<SysMenu> page = PageSortUtil.buildPage(
                dto.getCurrent(),
                dto.getSize(),
                sf,
                dto.getSortOrder(),
                SysMenu.class,
                null // 先不加单列 fallback，后面手动补 sort,id
        );
        // 若用户未指定合法排序，补默认排序
        if (page.orders().isEmpty()) {
            // sort ASC, id DESC
            page.setOrders(List.of()); // 清空后面由 wrapper.orderBy 排
        }
        // 直接用 wrapper 指定稳定排序
        wrapper.orderByAsc("sort").orderByDesc("id");
        page = menuService.page(page, wrapper);
        return ApiResponse.ok(page);
    }

    @GetMapping("/tree")
    @Operation(summary = "全部菜单树")
    public ApiResponse<List<MenuTreeVO>> tree() {
        return ApiResponse.ok(menuService.buildFullTree());
    }

    @PostMapping
    @Operation(summary = "新增或编辑菜单")
    public ApiResponse<Long> save(@Valid @RequestBody SaveMenuDTO dto) {
        return ApiResponse.ok(menuService.saveOrUpdate(dto));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除菜单")
    public ApiResponse<Void> delete(@Valid @RequestBody IdsDTO dto) {
        menuService.deleteMenus(dto.getIds());
        return ApiResponse.ok(null);
    }

    @PostMapping("/status")
    @Operation(summary = "修改菜单状态")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        menuService.changeStatus(dto);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "菜单详情")
    public ApiResponse<SysMenu> detail(@Parameter(description = "菜单ID") @PathVariable Long id) {
        return ApiResponse.ok(menuService.getMenuDetail(id));
    }
}

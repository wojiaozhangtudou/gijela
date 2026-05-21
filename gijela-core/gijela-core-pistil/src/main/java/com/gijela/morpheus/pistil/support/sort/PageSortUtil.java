package com.gijela.morpheus.pistil.support.sort;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;

/**
 * 反射 + @Sortable 方式构建分页与单字段排序。
 * 若排序字段非法或未标注 @Sortable，将回退到 fallback 列（若提供）。
 */
public final class PageSortUtil {
    private PageSortUtil() {}

    public static <T> Page<T> buildPage(int current, int size, String sortField, String sortOrder, Class<T> entityClass) {
        return buildPage(current, size, sortField, sortOrder, entityClass, null);
    }

    public static <T> Page<T> buildPage(int current, int size, String sortField, String sortOrder,
                                        Class<T> entityClass, String fallbackColumn) {
        Page<T> page = new Page<>(current, size);
        String column = null;
        if (StringUtils.hasText(sortField)) {
            try {
                column = SortableFieldResolver.toColumn(entityClass, sortField);
            } catch (IllegalArgumentException ignored) {
                column = null;
            }
        }
        boolean asc = "ASC".equalsIgnoreCase(sortOrder == null ? "DESC" : sortOrder);
        if (StringUtils.hasText(column)) {
            page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
        } else if (StringUtils.hasText(fallbackColumn)) {
            page.addOrder(OrderItem.desc(fallbackColumn));
        }
        return page;
    }
}

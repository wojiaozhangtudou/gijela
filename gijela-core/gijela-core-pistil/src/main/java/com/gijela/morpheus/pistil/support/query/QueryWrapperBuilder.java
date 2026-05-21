package com.gijela.morpheus.pistil.support.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;

public final class QueryWrapperBuilder {

    private QueryWrapperBuilder() {}

    public static <E> void apply(Object dto, QueryWrapper<E> wrapper) {
        if (dto == null || wrapper == null) {
            return;
        }
        Class<?> clazz = dto.getClass();
        for (Field f : clazz.getDeclaredFields()) {
            QueryField qf = f.getAnnotation(QueryField.class);
            if (qf == null) {
                continue;
            }
            f.setAccessible(true);
            Object val;
            try {
                val = f.get(dto);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (val == null) {
                continue;
            }
            String column = qf.column();
            if (!StringUtils.hasText(column)) {
                continue;
            }
            if (val instanceof String s) {
                if (qf.trim()) {
                    s = s.trim();
                }
                if (!StringUtils.hasText(s)) {
                    continue;
                }
                if (qf.op() == QueryOperator.LIKE) {
                    wrapper.like(column, s);
                } else {
                    wrapper.eq(column, s);
                }
            } else {
                wrapper.eq(column, val);
            }
        }
    }
}

package com.gijela.morpheus.pistil.support.sort;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 @Sortable 注解的排序字段解析器（纯 JDK 反射）。
 */
public final class SortableFieldResolver {
    private SortableFieldResolver() {}

    public static List<String> getSortableFields(Class<?> clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(Sortable.class)) {
                list.add(f.getName());
            }
        }
        return list;
    }

    public static String toColumn(Class<?> clazz, String field) {
        if (clazz == null || field == null || field.isEmpty()) {
            throw new IllegalArgumentException("字段或类型为空");
        }
        try {
            Field f = clazz.getDeclaredField(field);
            Sortable anno = f.getAnnotation(Sortable.class);
            if (anno == null) {
                throw new IllegalArgumentException("字段未标注 @Sortable: " + field);
            }
            if (!anno.value().isEmpty()) {
                return anno.value();
            }
            return camelToUnderline(field);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("非法排序字段: " + field);
        }
    }

    private static String camelToUnderline(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

package com.gijela.morpheus.pistil.support.sort;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记可排序字段。value 可指定数据库列名，缺省按驼峰转下划线。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sortable {
    String value() default "";
}

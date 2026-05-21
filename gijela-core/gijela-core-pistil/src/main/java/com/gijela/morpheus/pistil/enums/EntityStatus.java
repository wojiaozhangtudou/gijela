package com.gijela.morpheus.common.enums;

/**
 * 实体状态枚举：用于用户、部门、角色、菜单等实体的启用/禁用状态
 * 说明：0 禁用，1 启用（与数据库保持一致）
 */
public enum EntityStatus {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int value;
    private final String description;

    EntityStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据值获取枚举，用于数据库查询或转换
     */
    public static EntityStatus fromValue(int value) {
        for (EntityStatus status : EntityStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的 EntityStatus 值: " + value);
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return this == ENABLED;
    }

    /**
     * 检查是否禁用
     */
    public boolean isDisabled() {
        return this == DISABLED;
    }
}

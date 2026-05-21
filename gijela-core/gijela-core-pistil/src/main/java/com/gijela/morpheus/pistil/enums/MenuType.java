package com.gijela.morpheus.common.enums;

/**
 * 菜单类型枚举
 * 说明：D 目录，M 菜单，B 按钮（与数据库保持一致）
 */
public enum MenuType {
    DIRECTORY("D", "目录"),
    MENU("M", "菜单"),
    BUTTON("B", "按钮");

    private final String code;
    private final String description;

    MenuType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取枚举
     */
    public static MenuType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("MenuType 编码不能为空");
        }
        for (MenuType type : MenuType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的 MenuType 编码: " + code);
    }

    /**
     * 检查是否为目录
     */
    public boolean isDirectory() {
        return this == DIRECTORY;
    }

    /**
     * 检查是否为菜单
     */
    public boolean isMenu() {
        return this == MENU;
    }

    /**
     * 检查是否为按钮
     */
    public boolean isButton() {
        return this == BUTTON;
    }

    /**
     * 检查是否为容器（目录或菜单）
     */
    public boolean isContainer() {
        return this == DIRECTORY || this == MENU;
    }
}

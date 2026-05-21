package com.gijela.morpheus.llm.sdk.skill;

/**
 * 技能来源：内置（编译期注册）vs 本地（文件目录扫描）vs 远程 MCP server。
 */
public enum SkillSource {
    BUILTIN,
    LOCAL,
    MCP
}

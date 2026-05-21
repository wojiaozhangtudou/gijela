package com.gijela.morpheus.llm.sdk.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Skill SDK 配置项。前缀：{@code gijela.llm.skill}
 */
@ConfigurationProperties(prefix = "gijela.llm.skill")
public class SkillSdkProperties {

    /** 是否启用本地 SKILL.md 扫描。 */
    private boolean scanEnabled = true;

    /** 本地技能根目录。默认 skills（相对启动目录）。 */
    private String localPath = "skills";

    /** 是否启用热加载（默认关闭，由业务侧自行实现调度）。 */
    private boolean hotReloadEnabled = false;

    /** 热加载间隔（秒）。 */
    private int hotReloadIntervalSeconds = 30;

    public boolean isScanEnabled() { return scanEnabled; }
    public void setScanEnabled(boolean scanEnabled) { this.scanEnabled = scanEnabled; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public boolean isHotReloadEnabled() { return hotReloadEnabled; }
    public void setHotReloadEnabled(boolean hotReloadEnabled) { this.hotReloadEnabled = hotReloadEnabled; }

    public int getHotReloadIntervalSeconds() { return hotReloadIntervalSeconds; }
    public void setHotReloadIntervalSeconds(int hotReloadIntervalSeconds) { this.hotReloadIntervalSeconds = hotReloadIntervalSeconds; }
}

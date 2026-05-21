package com.gijela.morpheus.llm.sdk.mcp.skill;

import com.gijela.morpheus.llm.sdk.mcp.skill.McpToolBinding;
import java.util.List;

public interface McpToolBindingSource {
    public List<McpToolBinding> loadActiveBindings(String var1);
}

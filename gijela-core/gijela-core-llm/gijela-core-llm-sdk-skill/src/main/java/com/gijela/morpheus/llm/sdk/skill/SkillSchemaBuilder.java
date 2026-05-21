package com.gijela.morpheus.llm.sdk.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillSchemaBuilder {

    private SkillSchemaBuilder() {
    }

    public static Map<String, Object> functionTool(String name,
                                                   String description,
                                                   Map<String, Object> properties) {
        return functionTool(name, description, properties, List.of());
    }

    public static Map<String, Object> functionTool(String name,
                                                   String description,
                                                   Map<String, Object> properties,
                                                   List<String> required) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        ));
        return Map.of(
                "type", "function",
                "function", function
        );
    }
}

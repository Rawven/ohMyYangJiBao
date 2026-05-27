package com.fundtracker.service;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiToolRegistry {
    private final Map<String, AiTool> tools = new HashMap<>();

    public AiToolRegistry(List<AiTool> toolList) {
        toolList.forEach(t -> tools.put(t.getName(), t));
    }

    public AiTool getTool(String name) {
        return tools.get(name);
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return tools.values().stream().map(t -> Map.of(
            "type", "function",
            "function", Map.of(
                "name", t.getName(),
                "description", t.getDescription(),
                "parameters", t.getParameters()
            )
        )).toList();
    }
}

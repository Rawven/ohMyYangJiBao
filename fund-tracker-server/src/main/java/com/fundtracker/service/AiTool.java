package com.fundtracker.service;

import java.util.Map;

public interface AiTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameters();  // JSON Schema for function calling
    Object execute(Map<String, Object> args);
}

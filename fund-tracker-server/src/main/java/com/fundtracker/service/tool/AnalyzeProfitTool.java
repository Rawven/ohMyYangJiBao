package com.fundtracker.service.tool;

import com.fundtracker.service.AnalysisService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AnalyzeProfitTool implements AiTool {

    private final AnalysisService analysisService;

    public AnalyzeProfitTool(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public String getName() {
        return "analyze_profit";
    }

    @Override
    public String getDescription() {
        return "获取收益分析数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            return GetPortfolioSummaryTool.analysisDtoToMap(analysisService.getAnalysis());
        } catch (Exception e) {
            return Map.of("error", "获取收益分析失败: " + e.getMessage());
        }
    }
}

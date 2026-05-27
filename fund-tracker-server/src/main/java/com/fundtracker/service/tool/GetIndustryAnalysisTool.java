package com.fundtracker.service.tool;

import com.fundtracker.service.IndustryAnalysisService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetIndustryAnalysisTool implements AiTool {

    private final IndustryAnalysisService industryAnalysisService;

    public GetIndustryAnalysisTool(IndustryAnalysisService industryAnalysisService) {
        this.industryAnalysisService = industryAnalysisService;
    }

    @Override
    public String getName() {
        return "get_industry_analysis";
    }

    @Override
    public String getDescription() {
        return "获取行业板块分析数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "industry", Map.of(
                    "type", "string",
                    "description", "要重点分析的行业名称，如 半导体、新能源、医药、消费、金融、白酒、光伏等。留空则返回全市场行业概览"
                )
            ),
            "required", List.of()
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String industry = (String) args.get("industry");
            IndustryAnalysisService.IndustryAnalysis analysis = industryAnalysisService.getIndustryAnalysis(industry);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("analysis", analysis.getAnalysis());
            result.put("date", analysis.getDate() != null ? analysis.getDate().toString() : null);

            if (analysis.getIndustries() != null) {
                List<Map<String, Object>> industries = analysis.getIndustries().stream().map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("industryName", i.getIndustryName());
                    m.put("totalRatio", i.getTotalRatio() != null ? i.getTotalRatio().doubleValue() : null);
                    m.put("stockCount", i.getStockCount());
                    m.put("trend", i.getTrend());
                    return m;
                }).toList();
                result.put("industries", industries);
            }

            // 查询行业相关基金列表
            if (industry != null && !industry.isBlank()) {
                List<Map<String, Object>> relatedFunds = industryAnalysisService.searchRelatedFunds(industry);
                result.put("relatedFunds", relatedFunds);
                result.put("industry", industry);
                result.put("fundCount", relatedFunds.size());
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "获取行业分析失败: " + e.getMessage());
        }
    }
}

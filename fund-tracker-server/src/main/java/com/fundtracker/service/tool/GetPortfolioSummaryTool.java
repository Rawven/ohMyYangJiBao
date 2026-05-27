package com.fundtracker.service.tool;

import com.fundtracker.model.dto.AnalysisDTO;
import com.fundtracker.service.AnalysisService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetPortfolioSummaryTool implements AiTool {

    private final AnalysisService analysisService;

    public GetPortfolioSummaryTool(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public String getName() {
        return "get_portfolio_summary";
    }

    @Override
    public String getDescription() {
        return "获取持仓盈亏汇总数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            AnalysisDTO dto = analysisService.getAnalysis();
            return analysisDtoToMap(dto);
        } catch (Exception e) {
            return Map.of("error", "获取持仓汇总失败: " + e.getMessage());
        }
    }

    static Map<String, Object> analysisDtoToMap(AnalysisDTO dto) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMarketValue", dto.getTotalMarketValue() != null ? dto.getTotalMarketValue().doubleValue() : null);
        result.put("totalCost", dto.getTotalCost() != null ? dto.getTotalCost().doubleValue() : null);
        result.put("totalProfit", dto.getTotalProfit() != null ? dto.getTotalProfit().doubleValue() : null);
        result.put("totalProfitRate", dto.getTotalProfitRate() != null ? dto.getTotalProfitRate().doubleValue() : null);

        if (dto.getProfitTrend() != null) {
            List<Map<String, Object>> trend = dto.getProfitTrend().stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", p.getDate());
                m.put("totalProfit", p.getTotalProfit() != null ? p.getTotalProfit().doubleValue() : null);
                m.put("totalMarketValue", p.getTotalMarketValue() != null ? p.getTotalMarketValue().doubleValue() : null);
                return m;
            }).toList();
            result.put("profitTrend", trend);
        }

        if (dto.getDistribution() != null) {
            List<Map<String, Object>> dist = dto.getDistribution().stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fundName", d.getFundName());
                m.put("value", d.getValue() != null ? d.getValue().doubleValue() : null);
                m.put("percentage", d.getPercentage() != null ? d.getPercentage().doubleValue() : null);
                return m;
            }).toList();
            result.put("distribution", dist);
        }

        return result;
    }
}

package com.fundtracker.service.tool;

import com.fundtracker.service.IndexValuationService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetIndexValuationTool implements AiTool {

    private final IndexValuationService indexValuationService;

    public GetIndexValuationTool(IndexValuationService indexValuationService) {
        this.indexValuationService = indexValuationService;
    }

    @Override
    public String getName() {
        return "get_index_valuation";
    }

    @Override
    public String getDescription() {
        return "获取主要指数的估值数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            List<IndexValuationService.IndexValuation> valuations = indexValuationService.getValuations();
            List<Map<String, Object>> result = new ArrayList<>();
            for (IndexValuationService.IndexValuation v : valuations) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", v.name());
                item.put("code", v.code());
                item.put("price", v.price());
                item.put("changePct", v.changePct());
                item.put("pe", v.pe());
                item.put("amplitude", v.amplitude());
                item.put("turnover", v.turnover());
                item.put("high52w", v.high52w());
                item.put("low52w", v.low52w());
                item.put("pePercentile", v.pePercentile());
                item.put("level", v.level());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取指数估值失败: " + e.getMessage());
        }
    }
}

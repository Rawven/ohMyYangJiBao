package com.fundtracker.service.tool;

import com.fundtracker.model.dto.HoldingDTO;
import com.fundtracker.service.HoldingService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetPortfolioTool implements AiTool {

    private final HoldingService holdingService;

    public GetPortfolioTool(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @Override
    public String getName() {
        return "get_portfolio";
    }

    @Override
    public String getDescription() {
        return "获取当前基金持仓列表";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            List<HoldingDTO> holdings = holdingService.listHoldingDTOs();
            List<Map<String, Object>> result = new ArrayList<>();
            for (HoldingDTO h : holdings) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", h.getId());
                item.put("fundCode", h.getFundCode());
                item.put("fundName", h.getFundName());
                item.put("fundType", h.getFundType());
                item.put("shares", h.getShares() != null ? h.getShares().doubleValue() : null);
                item.put("costNav", h.getCostNav() != null ? h.getCostNav().doubleValue() : null);
                item.put("currentNav", h.getCurrentNav() != null ? h.getCurrentNav().doubleValue() : null);
                item.put("marketValue", h.getMarketValue() != null ? h.getMarketValue().doubleValue() : null);
                item.put("costValue", h.getCostValue() != null ? h.getCostValue().doubleValue() : null);
                item.put("profit", h.getProfit() != null ? h.getProfit().doubleValue() : null);
                item.put("profitRate", h.getProfitRate() != null ? h.getProfitRate().doubleValue() : null);
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取持仓列表失败: " + e.getMessage());
        }
    }
}

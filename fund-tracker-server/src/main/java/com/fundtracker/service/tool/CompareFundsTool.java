package com.fundtracker.service.tool;

import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.FundHolding;
import com.fundtracker.service.FundHoldingService;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CompareFundsTool implements AiTool {

    private final FundService fundService;
    private final FundHoldingService fundHoldingService;

    public CompareFundsTool(FundService fundService, FundHoldingService fundHoldingService) {
        this.fundService = fundService;
        this.fundHoldingService = fundHoldingService;
    }

    @Override
    public String getName() {
        return "compare_funds";
    }

    @Override
    public String getDescription() {
        return "对比多只基金的核心指标";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("codes", Map.of("type", "string", "description", "基金代码，多个用逗号分隔"));
        return Map.of("type", "object", "properties", properties, "required", List.of("codes"));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String codesStr = (String) args.get("codes");
            if (codesStr == null || codesStr.trim().isEmpty()) {
                return Map.of("error", "基金代码不能为空");
            }
            String[] codes = codesStr.split(",");
            List<Map<String, Object>> result = new ArrayList<>();

            for (String code : codes) {
                String trimmedCode = code.trim();
                if (trimmedCode.isEmpty()) continue;

                Fund fund = fundService.getFundByCode(trimmedCode);
                if (fund == null) {
                    result.add(Map.of("code", trimmedCode, "error", "未找到基金"));
                    continue;
                }

                Map<String, Object> fundMap = new LinkedHashMap<>();
                fundMap.put("code", fund.getCode());
                fundMap.put("name", fund.getName());
                fundMap.put("type", fund.getType());
                fundMap.put("nav", fund.getNav() != null ? fund.getNav().doubleValue() : null);
                fundMap.put("navDate", fund.getNavDate() != null ? fund.getNavDate().toString() : null);
                fundMap.put("dayIncrease", fund.getDayIncrease() != null ? fund.getDayIncrease().doubleValue() : null);
                fundMap.put("company", fund.getCompany());
                fundMap.put("establishDate", fund.getEstablishDate() != null ? fund.getEstablishDate().toString() : null);

                // 获取前三大持仓
                List<FundHolding> holdings = fundHoldingService.getHoldings(trimmedCode);
                List<Map<String, Object>> topHoldings = holdings.stream()
                        .sorted(Comparator.comparing(FundHolding::getHoldRatio,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(3)
                        .map(h -> {
                            Map<String, Object> hm = new LinkedHashMap<>();
                            hm.put("stockName", h.getStockName());
                            hm.put("stockCode", h.getStockCode());
                            hm.put("holdRatio", h.getHoldRatio() != null ? h.getHoldRatio().doubleValue() : null);
                            return hm;
                        })
                        .toList();
                fundMap.put("topHoldings", topHoldings);
                result.add(fundMap);
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "对比基金失败: " + e.getMessage());
        }
    }
}

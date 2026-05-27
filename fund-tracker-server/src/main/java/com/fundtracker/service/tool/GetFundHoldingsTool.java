package com.fundtracker.service.tool;

import com.fundtracker.model.entity.FundHolding;
import com.fundtracker.service.AiTool;
import com.fundtracker.service.FundHoldingService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetFundHoldingsTool implements AiTool {

    private final FundHoldingService fundHoldingService;

    public GetFundHoldingsTool(FundHoldingService fundHoldingService) {
        this.fundHoldingService = fundHoldingService;
    }

    @Override
    public String getName() {
        return "get_fund_holdings";
    }

    @Override
    public String getDescription() {
        return "获取单只基金的最新持仓股票列表（前十大重仓股），需传入基金代码";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "code", Map.of("type", "string", "description", "基金代码，如 110011")
            ),
            "required", List.of("code")
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String code = args != null ? (String) args.get("code") : null;
            if (code == null || code.trim().isEmpty()) {
                return Map.of("error", "基金代码不能为空");
            }

            List<FundHolding> holdings = fundHoldingService.getHoldings(code.trim());

            if (holdings.isEmpty()) {
                return Map.of(
                    "fundCode", code,
                    "holdings", List.of(),
                    "message", "暂无持仓数据，可能该基金为 ETF 联接基金（不直接持有股票）或数据尚未爬取"
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fundCode", code);
            result.put("reportDate", holdings.get(0).getReportDate() != null
                ? holdings.get(0).getReportDate().toString() : null);

            List<Map<String, Object>> stockList = holdings.stream().map(h -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stockName", h.getStockName());
                item.put("stockCode", h.getStockCode());
                item.put("holdRatio", h.getHoldRatio() != null ? h.getHoldRatio().doubleValue() : null);
                item.put("changeRatio", h.getChangeRatio() != null ? h.getChangeRatio().doubleValue() : null);
                return item;
            }).toList();

            result.put("holdings", stockList);
            result.put("count", stockList.size());
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取基金持仓失败: " + e.getMessage());
        }
    }
}

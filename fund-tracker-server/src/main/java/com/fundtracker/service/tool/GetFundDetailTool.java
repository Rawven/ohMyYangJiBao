package com.fundtracker.service.tool;

import com.fundtracker.model.entity.Fund;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetFundDetailTool implements AiTool {

    private final FundService fundService;

    public GetFundDetailTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_fund_detail";
    }

    @Override
    public String getDescription() {
        return "获取单只基金的详细信息";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", Map.of("type", "string", "description", "基金代码"));
        return Map.of("type", "object", "properties", properties, "required", List.of("code"));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String code = (String) args.get("code");
            if (code == null || code.trim().isEmpty()) {
                return Map.of("error", "基金代码不能为空");
            }
            Fund fund = fundService.getFundByCode(code.trim());
            if (fund == null) {
                return Map.of("error", "未找到基金: " + code);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", fund.getCode());
            result.put("name", fund.getName());
            result.put("type", fund.getType());
            result.put("nav", fund.getNav() != null ? fund.getNav().doubleValue() : null);
            result.put("navDate", fund.getNavDate() != null ? fund.getNavDate().toString() : null);
            result.put("dayIncrease", fund.getDayIncrease() != null ? fund.getDayIncrease().doubleValue() : null);
            result.put("company", fund.getCompany());
            result.put("establishDate", fund.getEstablishDate() != null ? fund.getEstablishDate().toString() : null);
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取基金详情失败: " + e.getMessage());
        }
    }
}

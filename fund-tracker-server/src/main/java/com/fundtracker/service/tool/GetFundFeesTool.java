package com.fundtracker.service.tool;

import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetFundFeesTool implements AiTool {

    private final FundService fundService;

    public GetFundFeesTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_fund_fees";
    }

    @Override
    public String getDescription() {
        return "获取基金费率信息，包括管理费、托管费、销售服务费等";
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

            Map<String, Object> fees = fundService.getFundFeeInfo(code.trim());
            if (fees.isEmpty()) {
                return Map.of("fundCode", code, "message", "暂未获取到费率信息");
            }

            fees.put("fundCode", code);
            return fees;
        } catch (Exception e) {
            return Map.of("error", "获取费率信息失败: " + e.getMessage());
        }
    }
}

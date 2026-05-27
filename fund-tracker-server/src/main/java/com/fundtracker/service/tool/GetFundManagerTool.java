package com.fundtracker.service.tool;

import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetFundManagerTool implements AiTool {

    private final FundService fundService;

    public GetFundManagerTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_fund_manager";
    }

    @Override
    public String getDescription() {
        return "获取基金经理信息，包括姓名、从业年限、管理基金数量、最佳回报等";
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

            Map<String, Object> detail = fundService.getFundManagerDetail(code.trim());
            if (detail.isEmpty()) {
                // 降级：只返回经理姓名
                String name = fundService.getFundManagerName(code.trim());
                if (name == null) {
                    return Map.of("fundCode", code, "message", "暂未获取到基金经理信息");
                }
                return Map.of("fundCode", code, "managerName", name);
            }

            detail.put("fundCode", code);
            return detail;
        } catch (Exception e) {
            return Map.of("error", "获取基金经理信息失败: " + e.getMessage());
        }
    }
}

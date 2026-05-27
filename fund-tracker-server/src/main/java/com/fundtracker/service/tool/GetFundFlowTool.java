package com.fundtracker.service.tool;

import com.fundtracker.service.FundFlowService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetFundFlowTool implements AiTool {

    private final FundFlowService fundFlowService;

    public GetFundFlowTool(FundFlowService fundFlowService) {
        this.fundFlowService = fundFlowService;
    }

    @Override
    public String getName() {
        return "get_fund_flow";
    }

    @Override
    public String getDescription() {
        return "获取基金资金流向数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            List<FundFlowService.FundFlowItem> flowList = fundFlowService.getFundFlowList();
            List<Map<String, Object>> result = new ArrayList<>();
            for (FundFlowService.FundFlowItem item : flowList) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fundCode", item.getFundCode());
                m.put("fundName", item.getFundName());
                m.put("fundType", item.getFundType());
                m.put("institutionRatio", item.getInstitutionRatio() != null ? item.getInstitutionRatio().doubleValue() : null);
                m.put("personalRatio", item.getPersonalRatio() != null ? item.getPersonalRatio().doubleValue() : null);
                m.put("netSubscribe", item.getNetSubscribe() != null ? item.getNetSubscribe().doubleValue() : null);
                m.put("scaleChangeRate", item.getScaleChangeRate());
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取资金流向失败: " + e.getMessage());
        }
    }
}

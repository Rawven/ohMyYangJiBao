package com.fundtracker.service.tool;

import com.fundtracker.model.entity.NavHistory;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetNavHistoryTool implements AiTool {

    private final FundService fundService;

    public GetNavHistoryTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_nav_history";
    }

    @Override
    public String getDescription() {
        return "获取基金的历史净值数据";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", Map.of("type", "string", "description", "基金代码"));
        properties.put("days", Map.of("type", "integer", "description", "最近天数，默认全部"));
        return Map.of("type", "object", "properties", properties, "required", List.of("code"));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String code = (String) args.get("code");
            if (code == null || code.trim().isEmpty()) {
                return Map.of("error", "基金代码不能为空");
            }

            // 先查本地数据库
            List<NavHistory> history = fundService.getNavHistory(code.trim());

            // 如果数据库为空，从天天基金拉取
            if (history == null || history.isEmpty()) {
                int days = 365;
                if (args.containsKey("days")) {
                    days = ((Number) args.get("days")).intValue();
                }
                history = fundService.fetchNavHistory(code.trim(), Math.max(days, 365));
            }

            // 按日期降序排列（最新的在前）
            List<NavHistory> sorted = new ArrayList<>(history);
            sorted.sort((a, b) -> b.getDate().compareTo(a.getDate()));

            // 如果指定了天数，截取最近N条
            if (args.containsKey("days")) {
                int days = ((Number) args.get("days")).intValue();
                if (days > 0 && days < sorted.size()) {
                    sorted = sorted.subList(0, days);
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (NavHistory nh : sorted) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", nh.getDate() != null ? nh.getDate().toString() : null);
                item.put("nav", nh.getNav() != null ? nh.getNav().doubleValue() : null);
                result.add(item);
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "获取净值历史失败: " + e.getMessage());
        }
    }
}

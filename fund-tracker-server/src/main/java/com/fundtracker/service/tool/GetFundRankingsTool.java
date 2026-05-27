package com.fundtracker.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class GetFundRankingsTool implements AiTool {

    private final FundMapper fundMapper;

    public GetFundRankingsTool(FundMapper fundMapper) {
        this.fundMapper = fundMapper;
    }

    @Override
    public String getName() {
        return "get_fund_rankings";
    }

    @Override
    public String getDescription() {
        return "获取基金排行，支持按类型（股票型、混合型、指数型、债券型、货币型）和涨跌幅排行。默认返回今日涨幅前 20 名";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string", "description", "基金类型，如 股票型、混合型、指数型、债券型、货币型。留空表示全部"));
        properties.put("orderBy", Map.of("type", "string", "description", "排序方式：dayIncrease(日涨跌幅) 或 nav(净值)，默认 dayIncrease"));
        properties.put("orderDir", Map.of("type", "string", "description", "排序方向：desc(降序) 或 asc(升序)，默认 desc"));
        properties.put("topN", Map.of("type", "integer", "description", "返回数量，默认 20，最大 50"));
        return Map.of("type", "object", "properties", properties, "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String type = args != null ? (String) args.get("type") : null;
            String orderBy = args != null ? (String) args.get("orderBy") : null;
            String orderDir = args != null ? (String) args.get("orderDir") : null;
            int topN = args != null && args.get("topN") != null
                ? Math.min(((Number) args.get("topN")).intValue(), 50)
                : 20;

            LambdaQueryWrapper<Fund> wrapper = new LambdaQueryWrapper<>();
            wrapper.gt(Fund::getNav, BigDecimal.ZERO)
                   .isNotNull(Fund::getDayIncrease)
                   .ne(Fund::getDayIncrease, BigDecimal.ZERO);

            if (type != null && !type.isBlank()) {
                wrapper.like(Fund::getType, type.trim());
            }

            // 排序
            boolean desc = !"asc".equals(orderDir);
            if ("nav".equals(orderBy)) {
                if (desc) wrapper.orderByDesc(Fund::getNav);
                else wrapper.orderByAsc(Fund::getNav);
            } else {
                // 默认按日涨跌幅
                if (desc) wrapper.orderByDesc(Fund::getDayIncrease);
                else wrapper.orderByAsc(Fund::getDayIncrease);
            }

            List<Fund> funds = fundMapper.selectPage(new Page<>(1, topN), wrapper).getRecords();

            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < funds.size(); i++) {
                Fund f = funds.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", i + 1);
                item.put("code", f.getCode());
                item.put("name", f.getName());
                item.put("type", f.getType());
                item.put("nav", f.getNav() != null ? f.getNav().doubleValue() : null);
                item.put("navDate", f.getNavDate() != null ? f.getNavDate().toString() : null);
                item.put("dayIncrease", f.getDayIncrease() != null
                    ? f.getDayIncrease().multiply(BigDecimal.valueOf(100)).doubleValue() : null);
                item.put("company", f.getCompany());
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", funds.size());
            result.put("orderBy", orderBy != null ? orderBy : "dayIncrease");
            result.put("orderDir", desc ? "desc" : "asc");
            result.put("items", items);
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取基金排行失败: " + e.getMessage());
        }
    }
}

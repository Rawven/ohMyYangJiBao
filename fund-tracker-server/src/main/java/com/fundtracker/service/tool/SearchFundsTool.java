package com.fundtracker.service.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class SearchFundsTool implements AiTool {

    private final FundService fundService;

    public SearchFundsTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "search_funds";
    }

    @Override
    public String getDescription() {
        return "搜索基金列表，支持按关键字、类型、基金公司筛选";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "搜索关键字（基金名称或代码）"));
        properties.put("type", Map.of("type", "string", "description", "基金类型"));
        properties.put("company", Map.of("type", "string", "description", "基金公司"));
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始", "default", 1));
        properties.put("size", Map.of("type", "integer", "description", "每页数量", "default", 20));
        properties.put("minNav", Map.of("type", "number", "description", "最小净值"));
        properties.put("maxNav", Map.of("type", "number", "description", "最大净值"));
        properties.put("minDayIncrease", Map.of("type", "number", "description", "最小日涨跌幅（如 0.01 表示 1%）"));
        properties.put("maxDayIncrease", Map.of("type", "number", "description", "最大日涨跌幅"));
        properties.put("minEstablishDate", Map.of("type", "string", "description", "最早成立日期（YYYY-MM-DD）"));
        return Map.of("type", "object", "properties", properties, "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String keyword = (String) args.get("keyword");
            String type = (String) args.get("type");
            String company = (String) args.get("company");

            int page = args.containsKey("page") ? ((Number) args.get("page")).intValue() : 1;
            int size = args.containsKey("size") ? ((Number) args.get("size")).intValue() : 20;

            BigDecimal minNav = parseBigDecimal(args.get("minNav"));
            BigDecimal maxNav = parseBigDecimal(args.get("maxNav"));
            BigDecimal minDayIncrease = parseBigDecimal(args.get("minDayIncrease"));
            BigDecimal maxDayIncrease = parseBigDecimal(args.get("maxDayIncrease"));
            String minEstablishDate = (String) args.get("minEstablishDate");

            IPage<Fund> fundPage = fundService.screenerQuery(
                    keyword, type, company,
                    minNav, maxNav, minDayIncrease, maxDayIncrease,
                    minEstablishDate, page, size
            );

            List<Map<String, Object>> items = new ArrayList<>();
            for (Fund fund : fundPage.getRecords()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", fund.getCode());
                item.put("name", fund.getName());
                item.put("type", fund.getType());
                item.put("nav", fund.getNav() != null ? fund.getNav().doubleValue() : null);
                item.put("navDate", fund.getNavDate() != null ? fund.getNavDate().toString() : null);
                item.put("dayIncrease", fund.getDayIncrease() != null ? fund.getDayIncrease().doubleValue() : null);
                item.put("company", fund.getCompany());
                item.put("establishDate", fund.getEstablishDate() != null ? fund.getEstablishDate().toString() : null);
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", fundPage.getTotal());
            result.put("page", fundPage.getCurrent());
            result.put("size", fundPage.getSize());
            result.put("items", items);
            return result;
        } catch (Exception e) {
            return Map.of("error", "搜索基金失败: " + e.getMessage());
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

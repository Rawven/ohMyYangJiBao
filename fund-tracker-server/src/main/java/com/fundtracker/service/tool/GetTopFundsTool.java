package com.fundtracker.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetTopFundsTool implements AiTool {

    private final FundMapper fundMapper;

    public GetTopFundsTool(FundMapper fundMapper) {
        this.fundMapper = fundMapper;
    }

    @Override
    public String getName() {
        return "get_top_funds";
    }

    @Override
    public String getDescription() {
        return "获取近期表现最好的基金列表（按日涨跌幅降序排列），用于推荐热门基金。可选参数：type 过滤基金类型，topN 控制数量（默认 20，最大 50）";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "type", Map.of("type", "string", "description", "基金类型过滤，如\"股票型\"、\"混合型\"、\"指数型\"等"),
                "topN", Map.of("type", "integer", "description", "返回数量，默认 20，最大 50")
            ),
            "required", List.of()
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            String type = args != null ? (String) args.get("type") : null;
            int topN = args != null && args.get("topN") != null
                ? Math.min(((Number) args.get("topN")).intValue(), 50)
                : 20;

            LambdaQueryWrapper<Fund> wrapper = new LambdaQueryWrapper<>();
            wrapper.gt(Fund::getNav, BigDecimal.ZERO)
                   .isNotNull(Fund::getDayIncrease)
                   .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                   .orderByDesc(Fund::getDayIncrease);

            if (type != null && !type.isBlank()) {
                wrapper.eq(Fund::getType, type.trim());
            }

            List<Fund> funds = fundMapper.selectPage(new Page<>(1, topN), wrapper).getRecords();

            List<Map<String, Object>> items = new ArrayList<>();
            for (Fund fund : funds) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", fund.getCode());
                item.put("name", fund.getName());
                item.put("type", fund.getType());
                item.put("nav", fund.getNav() != null ? fund.getNav().doubleValue() : null);
                item.put("navDate", fund.getNavDate() != null ? fund.getNavDate().toString() : null);
                item.put("dayIncrease", fund.getDayIncrease() != null
                    ? fund.getDayIncrease().multiply(BigDecimal.valueOf(100)).doubleValue() : null);
                item.put("company", fund.getCompany());
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", funds.size());
            result.put("items", items);
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取热门基金失败: " + e.getMessage());
        }
    }
}

package com.fundtracker.service.tool;

import com.fundtracker.mapper.NavHistoryMapper;
import com.fundtracker.model.entity.NavHistory;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class GetFundPerformanceTool implements AiTool {

    private final FundService fundService;
    private final NavHistoryMapper navHistoryMapper;

    public GetFundPerformanceTool(FundService fundService, NavHistoryMapper navHistoryMapper) {
        this.fundService = fundService;
        this.navHistoryMapper = navHistoryMapper;
    }

    @Override
    public String getName() {
        return "get_fund_performance";
    }

    @Override
    public String getDescription() {
        return "获取基金阶段收益率，包括近1周、近1月、近3月、近6月、近1年、近3年和今年以来";
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
            code = code.trim();

            // 先从数据库取已有历史净值
            List<NavHistory> dbHistory = navHistoryMapper.findByFundCode(code);

            // 如果需要更多数据，从网络拉取（最多拉 750 个交易日 ≈ 3 年）
            int needed = 750;
            List<NavHistory> history = new ArrayList<>(dbHistory);
            if (history.size() < needed) {
                List<NavHistory> fetched = fundService.fetchNavHistory(code, needed);
                // 合并去重
                Set<String> seen = new HashSet<>();
                for (NavHistory h : history) {
                    seen.add(h.getDate().toString());
                }
                for (NavHistory h : fetched) {
                    if (!seen.contains(h.getDate().toString())) {
                        history.add(h);
                    }
                }
            }

            if (history.isEmpty()) {
                return Map.of("fundCode", code, "message", "暂无净值数据");
            }

            // 按日期升序
            history.sort(Comparator.comparing(NavHistory::getDate));

            LocalDate today = LocalDate.now();
            // 计算各阶段基准日期
            Map<String, LocalDate> periods = new LinkedHashMap<>();
            periods.put("近1周", today.minusDays(7));
            periods.put("近1月", today.minusMonths(1));
            periods.put("近3月", today.minusMonths(3));
            periods.put("近6月", today.minusMonths(6));
            periods.put("近1年", today.minusYears(1));
            periods.put("近3年", today.minusYears(3));
            periods.put("今年以来", LocalDate.of(today.getYear(), 1, 1));

            BigDecimal latestNav = history.get(history.size() - 1).getNav();
            LocalDate latestDate = history.get(history.size() - 1).getDate();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fundCode", code);
            result.put("latestNav", latestNav.doubleValue());
            result.put("latestDate", latestDate.toString());

            List<Map<String, Object>> periodsResult = new ArrayList<>();
            for (Map.Entry<String, LocalDate> entry : periods.entrySet()) {
                String periodName = entry.getKey();
                LocalDate targetDate = entry.getValue();

                // 找到 targetDate 之后最近的一个净值日期
                NavHistory periodNav = null;
                for (int i = history.size() - 1; i >= 0; i--) {
                    if (!history.get(i).getDate().isAfter(targetDate)) {
                        periodNav = history.get(i);
                        break;
                    }
                }

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("period", periodName);
                if (periodNav != null && periodNav.getNav().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal returnRate = latestNav.subtract(periodNav.getNav())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(periodNav.getNav(), 2, RoundingMode.HALF_UP);
                    p.put("returnRate", returnRate.doubleValue());
                    p.put("startDate", periodNav.getDate().toString());
                    p.put("startNav", periodNav.getNav().doubleValue());
                } else {
                    p.put("returnRate", null);
                    p.put("startDate", null);
                    p.put("startNav", null);
                }
                periodsResult.add(p);
            }

            result.put("periods", periodsResult);
            return result;
        } catch (Exception e) {
            return Map.of("error", "获取阶段收益失败: " + e.getMessage());
        }
    }
}

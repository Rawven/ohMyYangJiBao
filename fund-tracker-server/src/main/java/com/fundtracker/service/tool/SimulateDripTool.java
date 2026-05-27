package com.fundtracker.service.tool;

import com.fundtracker.model.entity.NavHistory;
import com.fundtracker.service.FundService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class SimulateDripTool implements AiTool {

    private final FundService fundService;

    public SimulateDripTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "simulate_drip";
    }

    @Override
    public String getDescription() {
        return "定投收益模拟：基于基金历史净值，模拟每月固定金额定投的收益情况";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "code", Map.of("type", "string", "description", "基金代码，如 110011"),
                "amount", Map.of("type", "number", "description", "每月定投金额（元），默认 1000"),
                "months", Map.of("type", "integer", "description", "定投月数，默认 12，最大 60")
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

            double amount = args != null && args.get("amount") != null
                ? ((Number) args.get("amount")).doubleValue() : 1000;
            int months = args != null && args.get("months") != null
                ? Math.min(((Number) args.get("months")).intValue(), 60) : 12;

            // 拉取历史净值
            List<NavHistory> history = fundService.fetchNavHistory(code, 750);
            if (history.isEmpty()) {
                return Map.of("fundCode", code, "message", "暂无净值数据，无法模拟定投");
            }

            history.sort(Comparator.comparing(NavHistory::getDate));

            // 模拟定投：从最早净值日开始，每月投入
            LocalDate startDate = history.get(0).getDate();
            BigDecimal totalInvested = BigDecimal.ZERO;
            BigDecimal totalShares = BigDecimal.ZERO;
            int actualInvestments = 0;

            for (int i = 0; i < months; i++) {
                LocalDate investDate = startDate.plusMonths(i);
                if (investDate.isAfter(history.get(history.size() - 1).getDate())) break;

                // 找到该日期或之前最近的一个净值
                NavHistory navOnDate = null;
                for (int j = history.size() - 1; j >= 0; j--) {
                    if (!history.get(j).getDate().isAfter(investDate)) {
                        navOnDate = history.get(j);
                        break;
                    }
                }
                if (navOnDate == null || navOnDate.getNav().compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal investAmount = BigDecimal.valueOf(amount);
                BigDecimal shares = investAmount.divide(navOnDate.getNav(), 4, RoundingMode.HALF_UP);
                totalInvested = totalInvested.add(investAmount);
                totalShares = totalShares.add(shares);
                actualInvestments++;
            }

            if (actualInvestments == 0) {
                return Map.of("fundCode", code, "message", "净值数据不足，无法模拟");
            }

            BigDecimal latestNav = history.get(history.size() - 1).getNav();
            BigDecimal marketValue = totalShares.multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = marketValue.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitRate = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(totalInvested, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fundCode", code);
            result.put("monthlyAmount", amount);
            result.put("totalMonths", actualInvestments);
            result.put("totalInvested", totalInvested.doubleValue());
            result.put("totalShares", totalShares.setScale(2, RoundingMode.HALF_UP).doubleValue());
            result.put("latestNav", latestNav.doubleValue());
            result.put("marketValue", marketValue.doubleValue());
            result.put("profit", profit.doubleValue());
            result.put("profitRate", profitRate.doubleValue());
            result.put("startDate", startDate.toString());
            result.put("endDate", history.get(history.size() - 1).getDate().toString());

            return result;
        } catch (Exception e) {
            return Map.of("error", "定投模拟失败: " + e.getMessage());
        }
    }
}

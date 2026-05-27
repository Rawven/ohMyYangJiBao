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
public class GetFundRiskMetricsTool implements AiTool {

    private final FundService fundService;

    public GetFundRiskMetricsTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_fund_risk_metrics";
    }

    @Override
    public String getDescription() {
        return "获取基金风险指标，包括最大回撤、波动率等（基于近期净值数据计算）";
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

            // 拉取历史净值（最近 250 个交易日 ≈ 1 年）
            List<NavHistory> history = fundService.fetchNavHistory(code, 250);
            if (history.isEmpty()) {
                return Map.of("fundCode", code, "message", "暂无净值数据");
            }

            // 按日期升序
            history.sort(Comparator.nullsLast(Comparator.comparing(NavHistory::getDate)));

            // 1. 计算最大回撤
            BigDecimal maxDrawdown = BigDecimal.ZERO;
            BigDecimal peakNav = history.get(0).getNav();
            LocalDate peakDate = history.get(0).getDate();
            LocalDate troughDate = peakDate;

            for (NavHistory h : history) {
                if (h.getNav() == null) continue;
                if (h.getNav().compareTo(peakNav) > 0) {
                    peakNav = h.getNav();
                    peakDate = h.getDate();
                } else {
                    BigDecimal dd = h.getNav().subtract(peakNav)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(peakNav, 2, RoundingMode.HALF_UP);
                    if (dd.compareTo(maxDrawdown) < 0) {
                        maxDrawdown = dd;
                        troughDate = h.getDate();
                    }
                }
            }

            // 2. 计算日收益率波动率（近 60 个交易日）
            double volatility = 0;
            if (history.size() >= 30) {
                List<NavHistory> recent = history.subList(Math.max(0, history.size() - 60), history.size());
                List<Double> dailyReturns = new ArrayList<>();
                for (int i = 1; i < recent.size(); i++) {
                    double prev = recent.get(i - 1).getNav().doubleValue();
                    double curr = recent.get(i).getNav().doubleValue();
                    if (prev > 0) {
                        dailyReturns.add((curr - prev) / prev);
                    }
                }

                if (dailyReturns.size() >= 20) {
                    double mean = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double variance = dailyReturns.stream()
                        .mapToDouble(r -> Math.pow(r - mean, 2))
                        .average().orElse(0);
                    double dailyVol = Math.sqrt(variance);
                    volatility = dailyVol * Math.sqrt(252) * 100; // 年化波动率
                }
            }

            // 3. 计算近期涨跌统计
            long upDays = 0, downDays = 0;
            for (int i = 1; i < history.size(); i++) {
                double prev = history.get(i - 1).getNav().doubleValue();
                double curr = history.get(i).getNav().doubleValue();
                if (curr >= prev) upDays++;
                else downDays++;
            }

            BigDecimal winRate = (upDays + downDays) > 0
                ? BigDecimal.valueOf(upDays).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(upDays + downDays), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            LocalDate latestDate = history.get(history.size() - 1).getDate();
            LocalDate firstDate = history.get(0).getDate();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fundCode", code);
            result.put("dataRange", firstDate.toString() + " 至 " + latestDate.toString());
            result.put("dataDays", history.size());
            result.put("maxDrawdown", maxDrawdown.doubleValue());
            result.put("peakNav", peakNav.doubleValue());
            result.put("peakDate", peakDate.toString());
            result.put("troughDate", troughDate.toString());
            result.put("annualizedVolatility", BigDecimal.valueOf(volatility).setScale(2, RoundingMode.HALF_UP).doubleValue());
            result.put("winRate", winRate.doubleValue() + "%");
            result.put("upDays", upDays);
            result.put("downDays", downDays);

            return result;
        } catch (Exception e) {
            return Map.of("error", "获取风险指标失败: " + e.getMessage());
        }
    }
}

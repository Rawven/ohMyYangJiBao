package com.fundtracker.service.tool;

import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.FundHolding;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.mapper.HoldingMapper;
import com.fundtracker.service.FundService;
import com.fundtracker.service.FundHoldingService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AnalyzePortfolioRiskTool implements AiTool {

    private final HoldingMapper holdingMapper;
    private final FundHoldingService fundHoldingService;
    private final FundService fundService;

    public AnalyzePortfolioRiskTool(HoldingMapper holdingMapper,
                                    FundHoldingService fundHoldingService,
                                    FundService fundService) {
        this.holdingMapper = holdingMapper;
        this.fundHoldingService = fundHoldingService;
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "analyze_portfolio_risk";
    }

    @Override
    public String getDescription() {
        return "分析当前基金持仓的风险，包括持仓集中度、行业分散度、单只基金占比过高等";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            List<Holding> holdings = holdingMapper.selectList(null);
            if (holdings.isEmpty()) {
                return Map.of("message", "当前没有持仓数据，请先添加持仓");
            }

            double totalValue = 0;
            Map<String, Double> fundValues = new LinkedHashMap<>();
            for (Holding h : holdings) {
                Fund fund = fundService.getFundByCode(h.getFundCode());
                double nav = fund != null && fund.getNav() != null ? fund.getNav().doubleValue() : h.getCostNav().doubleValue();
                double value = h.getShares().doubleValue() * nav;
                fundValues.put(h.getFundCode(), value);
                totalValue += value;
            }

            if (totalValue <= 0) {
                return Map.of("message", "持仓总市值为 0，无法分析");
            }

            // 风险分析维度
            List<Map<String, Object>> warnings = new ArrayList<>();
            List<Map<String, Object>> distributions = new ArrayList<>();

            // 1. 单只基金集中度分析
            for (Map.Entry<String, Double> entry : fundValues.entrySet()) {
                double ratio = entry.getValue() / totalValue * 100;
                String fundCode = entry.getKey();

                Map<String, Object> dist = new LinkedHashMap<>();
                dist.put("fundCode", fundCode);
                dist.put("ratio", BigDecimal.valueOf(ratio).setScale(2, RoundingMode.HALF_UP).doubleValue());
                distributions.add(dist);

                if (ratio > 30) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("type", "集中度风险");
                    w.put("fundCode", fundCode);
                    w.put("message", String.format("该基金占比 %.1f%%，超过 30%%，集中度偏高", ratio));
                    w.put("suggestion", "建议适当减仓，单只基金占比控制在 20% 以内");
                    warnings.add(w);
                } else if (ratio > 20) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("type", "集中度关注");
                    w.put("fundCode", fundCode);
                    w.put("message", String.format("该基金占比 %.1f%%，接近 20%% 警戒线", ratio));
                    w.put("suggestion", "关注后续变化，可考虑分散到其他基金");
                    warnings.add(w);
                }
            }

            // 2. 持仓数量分析
            if (holdings.size() == 1) {
                warnings.add(Map.of(
                    "type", "持仓数量不足",
                    "message", "只有 1 只基金，没有分散化",
                    "suggestion", "建议持有 3-5 只不同风格的基金分散风险"
                ));
            } else if (holdings.size() > 8) {
                warnings.add(Map.of(
                    "type", "持仓数量过多",
                    "message", String.format("持有 %d 只基金，管理成本较高", holdings.size()),
                    "suggestion", "建议精简到 5-8 只核心基金"
                ));
            }

            // 3. 行业暴露分析（通过前十大持仓判断）
            Map<String, Double> industryExposure = new LinkedHashMap<>();
            for (Holding h : holdings) {
                List<FundHolding> fundHoldings = fundHoldingService.getHoldings(h.getFundCode());
                for (FundHolding fh : fundHoldings) {
                    if (fh.getHoldRatio() == null) continue;
                    // 简单按股票名称关键词归类
                    String industry = guessIndustry(fh.getStockName());
                    double weight = fh.getHoldRatio().doubleValue() * (fundValues.get(h.getFundCode()) / totalValue);
                    industryExposure.merge(industry, weight, Double::sum);
                }
            }

            if (!industryExposure.isEmpty()) {
                List<Map<String, Object>> industryList = industryExposure.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("industry", e.getKey());
                        m.put("exposure", BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP));
                        return m;
                    }).toList();

                // 检查行业集中度
                if (!industryList.isEmpty()) {
                    double topIndustry = (double) industryList.get(0).get("exposure");
                    if (topIndustry > 40) {
                        warnings.add(Map.of(
                            "type", "行业集中度风险",
                            "message", String.format("「%s」行业暴露 %.1f%%，过于集中", industryList.get(0).get("industry"), topIndustry),
                            "suggestion", "建议增加其他行业配置以分散风险"
                        ));
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalFunds", holdings.size());
                result.put("totalValue", BigDecimal.valueOf(totalValue).setScale(2, RoundingMode.HALF_UP));
                result.put("distributions", distributions);
                result.put("industryExposure", industryList);
                result.put("warnings", warnings);
                result.put("riskLevel", warnings.isEmpty() ? "低" : (warnings.size() <= 2 ? "中" : "高"));
                result.put("summary", generateSummary(holdings.size(), warnings.size()));
                return result;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalFunds", holdings.size());
            result.put("totalValue", BigDecimal.valueOf(totalValue).setScale(2, RoundingMode.HALF_UP));
            result.put("distributions", distributions);
            result.put("warnings", warnings);
            result.put("riskLevel", warnings.isEmpty() ? "低" : (warnings.size() <= 2 ? "中" : "高"));
            result.put("summary", generateSummary(holdings.size(), warnings.size()));
            return result;

        } catch (Exception e) {
            return Map.of("error", "持仓风险分析失败: " + e.getMessage());
        }
    }

    private String generateSummary(int fundCount, int warningCount) {
        if (warningCount == 0) return "持仓结构健康，风险较低";
        if (warningCount <= 2) return "持仓有少量风险点，建议参考预警建议适当调整";
        return "持仓风险较高，建议重点关注行业集中度和单只基金占比";
    }

    private String guessIndustry(String stockName) {
        if (stockName == null) return "其他";
        String n = stockName;
        if (n.contains("茅台") || n.contains("五粮液") || n.contains("白酒") || n.contains("食品") || n.contains("伊利") || n.contains("海天")) return "食品饮料";
        if (n.contains("药") || n.contains("医疗") || n.contains("生物") || n.contains("CXO") || n.contains("恒瑞") || n.contains("迈瑞")) return "医药生物";
        if (n.contains("半导体") || n.contains("芯片") || n.contains("电子") || n.contains("中兴") || n.contains("立讯") || n.contains("京东方")) return "电子/半导体";
        if (n.contains("宁德") || n.contains("新能源") || n.contains("光伏") || n.contains("隆基") || n.contains("比亚迪") || n.contains("阳光")) return "新能源";
        if (n.contains("银行") || n.contains("招行") || n.contains("保险") || n.contains("证券") || n.contains("平安") || n.contains("中信")) return "金融";
        if (n.contains("腾讯") || n.contains("阿里") || n.contains("百度") || n.contains("美团") || n.contains("互联") || n.contains("软件") || n.contains("科大")) return "互联网/科技";
        if (n.contains("地产") || n.contains("万科") || n.contains("保利")) return "房地产";
        if (n.contains("军工") || n.contains("航") || n.contains("中航")) return "国防军工";
        if (n.contains("黄金") || n.contains("有色") || n.contains("钢铁") || n.contains("矿产")) return "有色/资源";
        if (n.contains("汽车") || n.contains("长城") || n.contains("上汽") || n.contains("长安")) return "汽车";
        if (n.contains("电力") || n.contains("能源") || n.contains("煤炭") || n.contains("中石油") || n.contains("中石化")) return "能源/电力";
        return "其他";
    }
}

package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.mapper.FundHoldingMapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.FundHolding;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class IndustryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IndustryAnalysisService.class);
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000L; // 10 分钟
    private static final int MAX_FUNDS = 20;
    private static final int MAX_STOCKS = 30;

    private final FundMapper fundMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundHoldingService fundHoldingService;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;

    /** 内存缓存 */
    private IndustryAnalysis cachedAnalysis;
    private long cacheTimestamp;
    /** 是否正在后台预爬取 */
    private volatile boolean preScraping = false;

    public IndustryAnalysisService(FundMapper fundMapper,
                                    FundHoldingMapper fundHoldingMapper,
                                    FundHoldingService fundHoldingService,
                                    @Lazy DeepSeekService deepSeekService) {
        this.fundMapper = fundMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.fundHoldingService = fundHoldingService;
        this.deepSeekService = deepSeekService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 启动时后台预爬热门基金持仓，加速首次访问
     */
    @PostConstruct
    public void preWarm() {
        new Thread(() -> {
            try {
                Thread.sleep(30000); // 等应用先启动完毕
                List<Fund> funds = fundMapper.selectList(
                        new LambdaQueryWrapper<Fund>()
                                .isNotNull(Fund::getDayIncrease)
                                .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                                .orderByDesc(Fund::getDayIncrease)
                                .last("LIMIT " + MAX_FUNDS)
                );
                if (!funds.isEmpty()) {
                    List<String> codes = funds.stream().map(Fund::getCode).collect(Collectors.toList());
                    List<FundHolding> existing = fundHoldingMapper.selectList(
                            new LambdaQueryWrapper<FundHolding>().in(FundHolding::getFundCode, codes).last("LIMIT 1")
                    );
                    if (existing.isEmpty()) {
                        log.info("后台预爬 {} 只基金的持仓数据", codes.size());
                        preScraping = true;
                        scrapeHoldingsBatch(codes);
                    }
                }
            } catch (Exception e) {
                log.warn("后台预爬持仓失败: {}", e.getMessage());
            } finally {
                preScraping = false;
            }
        }, "holding-pre-warm").start();
    }

    /**
     * 获取基金持仓行业分析（含缓存，10分钟有效）
     * 如果指定 industry，则搜索该行业相关基金并出具针对性的分析报告
     */
    public IndustryAnalysis getIndustryAnalysis() {
        return getIndustryAnalysis(null);
    }

    public IndustryAnalysis getIndustryAnalysis(String industry) {
        // 非行业特定查询，使用现有缓存逻辑
        if (industry == null || industry.isBlank()) {
            return getGeneralIndustryAnalysis();
        }

        // 行业特定查询：搜索相关基金
        List<Fund> relevantFunds = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .and(w -> w.like(Fund::getName, industry).or().like(Fund::getType, industry))
                        .isNotNull(Fund::getDayIncrease)
                        .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                        .orderByDesc(Fund::getDayIncrease)
                        .last("LIMIT " + MAX_FUNDS)
        );

        if (relevantFunds.isEmpty()) {
            // 没找到该行业基金，回退到全市场分析并标记
            IndustryAnalysis general = getGeneralIndustryAnalysis();
            String fallbackText = "⚠️ 未找到名称或类型包含「" + industry + "」的基金。\n\n基于全市场基金持仓的整体分析（仅筛选出与「" + industry + "」相关的行业）：\n" + general.getAnalysis();
            List<IndustryItem> filtered = general.getIndustries().stream()
                    .filter(i -> i.getIndustryName() != null && i.getIndustryName().contains(industry))
                    .toList();
            return new IndustryAnalysis(fallbackText, filtered.isEmpty() ? general.getIndustries() : filtered, general.getDate());
        }

        // 获取这些基金的持仓
        List<String> fundCodes = relevantFunds.stream().map(Fund::getCode).toList();
        List<FundHolding> allHoldings = fundHoldingMapper.selectList(
                new LambdaQueryWrapper<FundHolding>()
                        .in(FundHolding::getFundCode, fundCodes)
                        .isNotNull(FundHolding::getStockName)
                        .isNotNull(FundHolding::getHoldRatio)
        );

        // 如果没有持仓，主动爬取
        if (allHoldings.isEmpty()) {
            log.info("行业「{}」相关基金持仓为空，主动爬取 {} 只", industry, fundCodes.size());
            scrapeHoldingsBatch(fundCodes);
            allHoldings = fundHoldingMapper.selectList(
                    new LambdaQueryWrapper<FundHolding>()
                            .in(FundHolding::getFundCode, fundCodes)
                            .isNotNull(FundHolding::getStockName)
                            .isNotNull(FundHolding::getHoldRatio)
            );
        }

        // 按股票汇总持仓占比
        Map<String, BigDecimal> stockRatios = new LinkedHashMap<>();
        for (FundHolding h : allHoldings) {
            BigDecimal ratio = h.getHoldRatio() != null ? h.getHoldRatio() : BigDecimal.ZERO;
            stockRatios.merge(h.getStockName(), ratio, BigDecimal::add);
        }

        List<String> topStocks = stockRatios.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(MAX_STOCKS)
                .map(Map.Entry::getKey)
                .toList();

        // 如果爬取后仍无持仓数据（如 ETF 联接基金不直接持股），改用基金元数据生成分析
        String stockListStr;
        String analysisPrompt;
        if (topStocks.isEmpty()) {
            // 用基金名称、类型、涨跌幅等元数据生成分析
            StringBuilder fundInfo = new StringBuilder();
            for (Fund f : relevantFunds) {
                fundInfo.append(String.format("%s(%s) %s 净值%.4f 日涨跌%s%%",
                        f.getName(), f.getCode(), f.getType(),
                        f.getNav() != null ? f.getNav() : BigDecimal.ZERO,
                        f.getDayIncrease() != null
                                ? f.getDayIncrease().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                                : "0.00"));
                fundInfo.append("\n");
            }
            stockListStr = "（该行业基金不直接披露个股持仓，以下为相关基金基本信息）\n" + fundInfo;
            analysisPrompt = String.format("""
                    用户想了解「%s」行业的投资分析。

                    以下是与该行业相关的基金列表（共%d只）：
                    %s

                    请从以下角度进行专业分析（控制在250字以内）：
                    1. %s行业当前景气度及发展趋势判断
                    2. 相关基金的基本特点（类型分布、规模特征）
                    3. 投资该行业需要注意的风险
                    4. 一句话总结

                    请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：
                    {
                      "analysis": "分析内容...",
                      "industries": [
                        {"name": "%s", "ratio": 占比数值, "stockCount": %d, "trend": "stable"}
                      ]
                    }
                    """, industry, relevantFunds.size(), fundInfo, industry, industry, relevantFunds.size());
        } else {
            stockListStr = String.join("、", topStocks);
            analysisPrompt = String.format("""
                    用户想了解「%s」行业的投资分析。

                    相关基金共 %d 只，持有以下股票：
                    %s

                    请从以下角度进行专业分析（控制在250字以内）：
                    1. %s行业当前景气度及发展趋势判断
                    2. 相关基金持仓特点（集中度、主要配置方向）
                    3. 投资该行业需要注意的风险
                    4. 一句话总结

                    请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：
                    {
                      "analysis": "分析内容...",
                      "industries": [
                        {"name": "%s", "ratio": 占比数值, "stockCount": 股票数, "trend": "up/down/stable"}
                      ]
                    }
                    """, industry, relevantFunds.size(), stockListStr, industry, industry);
        }

        String prompt = analysisPrompt;

        String response = deepSeekService.callDeepSeekWithPrompt(prompt);
        IndustryAnalysis analysis = parseAnalysisResponse(response);
        analysis.setDate(LocalDate.now());
        return analysis;
    }

    /**
     * 查询某个行业相关的基金列表（供工具返回）
     */
    public List<Map<String, Object>> searchRelatedFunds(String industry) {
        if (industry == null || industry.isBlank()) return List.of();
        List<Fund> funds = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .and(w -> w.like(Fund::getName, industry).or().like(Fund::getType, industry))
                        .isNotNull(Fund::getNav)
                        .ne(Fund::getNav, BigDecimal.ZERO)
                        .orderByDesc(Fund::getDayIncrease)
                        .last("LIMIT 10")
        );
        return funds.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", f.getCode());
            m.put("name", f.getName());
            m.put("type", f.getType());
            m.put("nav", f.getNav() != null ? f.getNav().doubleValue() : null);
            m.put("dayIncrease", f.getDayIncrease() != null ? f.getDayIncrease().doubleValue() : null);
            return m;
        }).toList();
    }

    /**
     * 原全市场行业分析（内部方法，保持缓存）
     */
    private IndustryAnalysis getGeneralIndustryAnalysis() {
        // 缓存命中
        if (cachedAnalysis != null
                && System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS) {
            return cachedAnalysis;
        }

        // 1. 获取有数据的基金，取前 20 只
        List<Fund> funds = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .isNotNull(Fund::getDayIncrease)
                        .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                        .orderByDesc(Fund::getDayIncrease)
                        .last("LIMIT " + MAX_FUNDS)
        );

        if (funds.isEmpty()) {
            log.warn("数据库中无基金数据，行业分析返回空");
            IndustryAnalysis empty = new IndustryAnalysis("暂无基金持仓数据",
                    Collections.emptyList(), LocalDate.now());
            cacheResult(empty);
            return empty;
        }

        // 2. 获取这些基金的持仓数据
        List<String> fundCodes = funds.stream()
                .map(Fund::getCode)
                .collect(Collectors.toList());
        List<FundHolding> allHoldings = fundHoldingMapper.selectList(
                new LambdaQueryWrapper<FundHolding>()
                        .in(FundHolding::getFundCode, fundCodes)
                        .isNotNull(FundHolding::getStockName)
                        .isNotNull(FundHolding::getHoldRatio)
        );

        // 3. 如果数据库无持仓数据，主动爬取（多线程并行）
        if (allHoldings.isEmpty()) {
            log.info("持仓数据为空，主动爬取 {} 只基金的持仓", fundCodes.size());
            scrapeHoldingsBatch(fundCodes);
            // 重新查询
            allHoldings = fundHoldingMapper.selectList(
                    new LambdaQueryWrapper<FundHolding>()
                            .in(FundHolding::getFundCode, fundCodes)
                            .isNotNull(FundHolding::getStockName)
                            .isNotNull(FundHolding::getHoldRatio)
            );
        }

        // 4. 按股票名称汇总持仓占比，排序后取前 30
        Map<String, BigDecimal> stockRatios = new LinkedHashMap<>();
        for (FundHolding h : allHoldings) {
            BigDecimal ratio = h.getHoldRatio() != null ? h.getHoldRatio() : BigDecimal.ZERO;
            stockRatios.merge(h.getStockName(), ratio, BigDecimal::add);
        }

        List<String> topStocks = stockRatios.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(MAX_STOCKS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topStocks.isEmpty()) {
            log.warn("无有效的持仓数据");
            IndustryAnalysis empty = new IndustryAnalysis("暂无持仓数据",
                    Collections.emptyList(), LocalDate.now());
            cacheResult(empty);
            return empty;
        }

        // 4. 调用 DeepSeek 分析行业分类
        String stockListStr = String.join("、", topStocks);
        String prompt = String.format("""
                分析以下基金持仓股票所属的行业板块，并按行业汇总，分析各行业景气度及投资价值。
                持仓股票：%s

                请严格按以下 JSON 格式返回（不要包含 markdown 代码块标记）：
                {
                  "analysis": "总体分析...",
                  "industries": [
                    {"name": "食品饮料", "ratio": 25.5, "stockCount": 8, "trend": "up"},
                    {"name": "医药生物", "ratio": 15.2, "stockCount": 5, "trend": "stable"},
                    {"name": "电子", "ratio": 12.0, "stockCount": 4, "trend": "down"}
                  ]
                }
                """, stockListStr);

        String response = deepSeekService.callDeepSeekWithPrompt(prompt);
        IndustryAnalysis analysis = parseAnalysisResponse(response);

        cacheResult(analysis);
        return analysis;
    }

    /**
     * 多线程并行爬取基金持仓数据
     */
    private void scrapeHoldingsBatch(List<String> fundCodes) {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(fundCodes.size());
        AtomicInteger success = new AtomicInteger(0);

        for (String code : fundCodes) {
            executor.submit(() -> {
                try {
                    List<FundHolding> holdings = fundHoldingService.getHoldings(code);
                    if (!holdings.isEmpty()) {
                        success.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("爬取 {} 持仓失败: {}", code, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("持仓爬取完成: {}/{} 成功", success.get(), fundCodes.size());
    }

    /**
     * 解析 DeepSeek 返回的 JSON 文本为 IndustryAnalysis 对象
     */
    private IndustryAnalysis parseAnalysisResponse(String response) {
        try {
            // 清理可能的 markdown 代码块标记
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // 提取 JSON 对象
            int jsonStart = cleaned.indexOf('{');
            int jsonEnd = cleaned.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                log.warn("DeepSeek 响应中未找到 JSON: {}", response);
                return new IndustryAnalysis(response, Collections.emptyList(), LocalDate.now());
            }

            String json = cleaned.substring(jsonStart, jsonEnd + 1);
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String analysisText = (String) map.getOrDefault("analysis", response);

            List<IndustryItem> industries = new ArrayList<>();
            Object rawList = map.get("industries");
            if (rawList instanceof List<?> rawIndustries) {
                for (Object obj : rawIndustries) {
                    if (obj instanceof Map) {
                        Map<String, Object> item = (Map<String, Object>) obj;
                        String name = (String) item.getOrDefault("name", "");
                        BigDecimal ratio = BigDecimal.ZERO;
                        if (item.get("ratio") != null) {
                            ratio = new BigDecimal(item.get("ratio").toString());
                        }
                        int count = 0;
                        if (item.get("stockCount") != null) {
                            count = ((Number) item.get("stockCount")).intValue();
                        }
                        String trend = (String) item.getOrDefault("trend", "stable");
                        industries.add(new IndustryItem(name, ratio, count, trend));
                    }
                }
            }

            return new IndustryAnalysis(analysisText, industries, LocalDate.now());
        } catch (Exception e) {
            log.warn("解析行业分析响应失败: {}", e.getMessage());
            return new IndustryAnalysis(response, Collections.emptyList(), LocalDate.now());
        }
    }

    private void cacheResult(IndustryAnalysis analysis) {
        this.cachedAnalysis = analysis;
        this.cacheTimestamp = System.currentTimeMillis();
    }

    // ==================== 内部类 ====================

    public static class IndustryAnalysis {
        private String analysis;
        private List<IndustryItem> industries;
        private LocalDate date;

        public IndustryAnalysis() {}

        public IndustryAnalysis(String analysis, List<IndustryItem> industries, LocalDate date) {
            this.analysis = analysis;
            this.industries = industries;
            this.date = date;
        }

        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
        public List<IndustryItem> getIndustries() { return industries; }
        public void setIndustries(List<IndustryItem> industries) { this.industries = industries; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
    }

    public static class IndustryItem {
        private String industryName;
        private BigDecimal totalRatio;
        private int stockCount;
        private String trend;

        public IndustryItem() {}

        public IndustryItem(String industryName, BigDecimal totalRatio,
                            int stockCount, String trend) {
            this.industryName = industryName;
            this.totalRatio = totalRatio;
            this.stockCount = stockCount;
            this.trend = trend;
        }

        public String getIndustryName() { return industryName; }
        public void setIndustryName(String industryName) { this.industryName = industryName; }
        public BigDecimal getTotalRatio() { return totalRatio; }
        public void setTotalRatio(BigDecimal totalRatio) { this.totalRatio = totalRatio; }
        public int getStockCount() { return stockCount; }
        public void setStockCount(int stockCount) { this.stockCount = stockCount; }
        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }
    }
}

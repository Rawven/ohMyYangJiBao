package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.FundHolding;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FundFlowService {

    private static final Logger log = LoggerFactory.getLogger(FundFlowService.class);
    private static final String ARCHIVES_API = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=%s&code=%s&rt=%f";
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000L; // 1 小时（季频数据不需要频繁刷新）
    private static final int MAX_FUNDS = 50;

    private final DeepSeekService deepSeekService;
    private final FundHoldingService fundHoldingService;
    private final FundMapper fundMapper;

    private List<FundFlowItem> cachedFlowList;
    private long cacheTimestamp;

    public FundFlowService(@Lazy DeepSeekService deepSeekService,
                           FundHoldingService fundHoldingService,
                           FundMapper fundMapper) {
        this.deepSeekService = deepSeekService;
        this.fundHoldingService = fundHoldingService;
        this.fundMapper = fundMapper;
    }

    /**
     * 获取规模变动数据
     */
    public List<ScaleChange> getScaleChanges(String fundCode) {
        return parseScaleData(fetchApi("gmbd", fundCode));
    }

    /**
     * 获取持有人结构数据
     */
    public List<HolderStructure> getHolderStructures(String fundCode) {
        return parseHolderData(fetchApi("cyrjg", fundCode));
    }

    /**
     * 后台预爬资金流向数据，加速首次访问
     */
    @PostConstruct
    public void preWarm() {
        new Thread(() -> {
            try {
                Thread.sleep(45000); // 等应用启动完毕
                List<Fund> funds = fundMapper.selectList(
                        new LambdaQueryWrapper<Fund>()
                                .gt(Fund::getNav, BigDecimal.ZERO)
                                .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                                .orderByDesc(Fund::getNav)
                                .last("LIMIT " + MAX_FUNDS)
                );
                if (!funds.isEmpty()) {
                    getFlowList(funds);
                }
            } catch (Exception e) {
                log.warn("后台预爬资金流向失败: {}", e.getMessage());
            }
        }, "fundflow-pre-warm").start();
    }

    /**
     * 获取结构化资金流向列表（缓存1小时）
     */
    public List<FundFlowItem> getFundFlowList() {
        if (cachedFlowList != null
                && System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS) {
            return cachedFlowList;
        }

        List<Fund> funds = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .gt(Fund::getNav, BigDecimal.ZERO)
                        .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                        .orderByDesc(Fund::getNav)
                        .last("LIMIT " + MAX_FUNDS)
        );

        if (funds.isEmpty()) return Collections.emptyList();

        cachedFlowList = getFlowList(funds);
        cacheTimestamp = System.currentTimeMillis();
        return cachedFlowList;
    }

    private List<FundFlowItem> getFlowList(List<Fund> funds) {
        List<FundFlowItem> result = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(funds.size());

        for (Fund fund : funds) {
            executor.submit(() -> {
                try {
                    List<ScaleChange> scales = getScaleChanges(fund.getCode());
                    List<HolderStructure> holders = getHolderStructures(fund.getCode());

                    FundFlowItem item = new FundFlowItem();
                    item.fundCode = fund.getCode();
                    item.fundName = fund.getName();
                    item.fundType = fund.getType();

                    if (!holders.isEmpty()) {
                        HolderStructure latest = holders.get(0);
                        item.institutionRatio = latest.institutionRatio;
                        item.personalRatio = latest.personalRatio;
                    }

                    if (!scales.isEmpty()) {
                        ScaleChange latest = scales.get(0);
                        if (latest.subscribe != null && latest.redeem != null) {
                            item.netSubscribe = latest.subscribe.subtract(latest.redeem);
                        }
                        item.scaleChangeRate = latest.changeRate;
                    }

                    result.add(item);
                } catch (Exception e) {
                    log.warn("获取 {} 资金流向失败: {}", fund.getCode(), e.getMessage());
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

        // 按机构持有比例降序排列
        result.sort((a, b) -> {
            BigDecimal ba = a.institutionRatio != null ? a.institutionRatio : BigDecimal.ZERO;
            BigDecimal bb = b.institutionRatio != null ? b.institutionRatio : BigDecimal.ZERO;
            return bb.compareTo(ba);
        });
        return result;
    }

    /**
     * 获取资金流向的LLM分析
     */
    public String analyzeFlow(String fundCode, String fundName, String fundType) {
        List<ScaleChange> scales = getScaleChanges(fundCode);
        List<HolderStructure> holders = getHolderStructures(fundCode);

        if (scales.isEmpty()) {
            return "暂无资金流向数据";
        }

        // 最近4期申购赎回数据
        String recentScale = scales.stream()
                .limit(4)
                .map(s -> String.format("%s: 申购%.2f亿份, 赎回%.2f亿份, 期末规模%.2f亿元, 规模变动%s",
                        s.date, s.subscribe, s.redeem, s.netNav, s.changeRate))
                .collect(Collectors.joining("\n"));

        // 最近持有人结构
        String holderInfo = holders.isEmpty() ? "暂无持有人结构数据" :
                holders.stream()
                        .limit(2)
                        .map(h -> String.format("%s: 机构持有%.2f%%, 个人持有%.2f%%, 内部持有%.2f%%",
                                h.date, h.institutionRatio, h.personalRatio, h.insideRatio))
                        .collect(Collectors.joining("\n"));

        // 持仓数据
        List<FundHolding> holdings = fundHoldingService.getHoldings(fundCode);
        String holdingInfo = holdings.stream()
                .limit(5)
                .map(h -> String.format("%s(占比%.2f%%)", h.getStockName(), h.getHoldRatio()))
                .collect(Collectors.joining(", "));

        String prompt = String.format("""
                你是一个专业的基金资金流向分析助手。请基于以下数据分析基金的资金动向和机构态度。

                基金名称：%s
                基金类型：%s

                【最近4期申赎数据】
                %s

                【最近持有人结构】
                %s

                【前五大持仓】
                %s

                请从以下几个角度分析（控制在300字以内）：
                1. 资金流入流出趋势：近期是净申购还是净赎回，规模在扩大还是缩小
                2. 机构态度：机构持有比例的变化趋势，机构是在买入还是卖出
                3. 资金流向与持仓的关联：资金流向是否与持仓行业表现一致
                4. 综合评价：一句话总结该基金目前的资金流向特点
                """, fundName, fundType, recentScale, holderInfo, holdingInfo);

        return deepSeekService.callDeepSeekWithPrompt(prompt);
    }

    private String fetchApi(String type, String code) {
        try {
            String url = String.format(ARCHIVES_API, type, code, Math.random());
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://fundf10.eastmoney.com/");

            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("获取 {} 数据失败 type={}: {}", code, type, e.getMessage());
            return "";
        }
    }

    private List<ScaleChange> parseScaleData(String raw) {
        List<ScaleChange> list = new ArrayList<>();
        if (raw.isEmpty()) return list;

        // 解析表格行: <tr><td>2026-03-31</td><td class='tor'>4.31</td>...
        Pattern rowPattern = Pattern.compile(
                "<tr><td>(\\d{4}-\\d{2}-\\d{2})</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td></tr>"
        );
        Matcher m = rowPattern.matcher(raw);
        while (m.find()) {
            ScaleChange s = new ScaleChange();
            s.date = m.group(1);
            s.subscribe = parseNum(m.group(2));
            s.redeem = parseNum(m.group(3));
            s.totalShares = parseNum(m.group(4));
            s.netNav = parseNum(m.group(5));
            s.changeRate = m.group(6);
            list.add(s);
        }
        return list;
    }

    private List<HolderStructure> parseHolderData(String raw) {
        List<HolderStructure> list = new ArrayList<>();
        if (raw.isEmpty()) return list;

        Pattern rowPattern = Pattern.compile(
                "<tr><td>(\\d{4}-\\d{2}-\\d{2})</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td>" +
                "<td[^>]*>([^<]*)</td></tr>"
        );
        Matcher m = rowPattern.matcher(raw);
        while (m.find()) {
            HolderStructure h = new HolderStructure();
            h.date = m.group(1);
            h.institutionRatio = parsePercent(m.group(2));
            h.personalRatio = parsePercent(m.group(3));
            h.insideRatio = parsePercent(m.group(4));
            h.totalShares = parseNum(m.group(5));
            list.add(h);
        }
        return list;
    }

    private BigDecimal parseNum(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("---")) return null;
        try {
            return new BigDecimal(val.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parsePercent(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("---")) return null;
        try {
            return new BigDecimal(val.trim().replace("%", "").replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    public static class ScaleChange {
        public String date;
        public BigDecimal subscribe;  // 期间申购
        public BigDecimal redeem;     // 期间赎回
        public BigDecimal totalShares; // 期末总份额
        public BigDecimal netNav;      // 期末净资产
        public String changeRate;     // 净资产变动率
    }

    public static class HolderStructure {
        public String date;
        public BigDecimal institutionRatio; // 机构持有比例
        public BigDecimal personalRatio;     // 个人持有比例
        public BigDecimal insideRatio;      // 内部持有比例
        public BigDecimal totalShares;      // 总份额
    }

    /**
     * 资金流向展示用 DTO
     */
    public static class FundFlowItem {
        private String fundCode;
        private String fundName;
        private String fundType;
        private BigDecimal institutionRatio; // 机构持有比例
        private BigDecimal personalRatio;    // 个人持有比例
        private BigDecimal netSubscribe;     // 净申购（亿份）
        private String scaleChangeRate;      // 规模变动率

        public String getFundCode() { return fundCode; }
        public void setFundCode(String v) { fundCode = v; }
        public String getFundName() { return fundName; }
        public void setFundName(String v) { fundName = v; }
        public String getFundType() { return fundType; }
        public void setFundType(String v) { fundType = v; }
        public BigDecimal getInstitutionRatio() { return institutionRatio; }
        public void setInstitutionRatio(BigDecimal v) { institutionRatio = v; }
        public BigDecimal getPersonalRatio() { return personalRatio; }
        public void setPersonalRatio(BigDecimal v) { personalRatio = v; }
        public BigDecimal getNetSubscribe() { return netSubscribe; }
        public void setNetSubscribe(BigDecimal v) { netSubscribe = v; }
        public String getScaleChangeRate() { return scaleChangeRate; }
        public void setScaleChangeRate(String v) { scaleChangeRate = v; }
    }
}

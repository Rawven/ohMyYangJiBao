package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.mapper.NavHistoryMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.NavHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DataSyncService {
    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);
    private static final String FUND_LIST_URL = "https://fund.eastmoney.com/js/fundcode_search.js";
    private static final String REALTIME_URL = "https://fundgz.1234567.com.cn/js/{code}.js";
    private static final String HISTORY_URL = "https://api.fund.eastmoney.com/f10/lsjz?fundCode={code}&pageIndex=1&pageSize=30";
    private static final int BATCH_THREADS = 30;

    private final RestTemplate restTemplate;
    private final FundMapper fundMapper;
    private final NavHistoryMapper navHistoryMapper;

    // 热门基金代码（首页推荐和持仓演示用）
    private static final List<String> HOT_FUNDS = List.of(
        "110011", "005827", "008283", "110003", "000001",
        "002001", "070001", "040001", "160105", "233001",
        "260108", "163402",
        // 更多常见基金
        "161725", "008282", "000051", "000311", "000696",
        "001112", "001475", "002190", "003095", "003745",
        "004241", "004997", "005644", "005827", "006113",
        "006228", "006345", "006648", "007301", "007494",
        "007872", "008086", "008963", "009265", "009548",
        "009714", "010180", "010198", "010327", "010454"
    );

    public DataSyncService(RestTemplate restTemplate, FundMapper fundMapper, NavHistoryMapper navHistoryMapper) {
        this.restTemplate = restTemplate;
        this.fundMapper = fundMapper;
        this.navHistoryMapper = navHistoryMapper;
    }

    /**
     * 启动后 30 秒首次同步，之后每 2 小时自动同步
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 2 * 60 * 60 * 1000)
    public void autoSync() {
        log.info("定时同步开始");
        try {
            syncAll();
            log.info("定时同步完成");
        } catch (Exception e) {
            log.error("定时同步失败", e);
        }
    }

    /** 同步全部：先拉全量基金列表，再更新热门基金净值 */
    public SyncResult syncAll() {
        SyncResult result = new SyncResult();
        result.totalFunds = syncFundList(result);
        syncNavs(HOT_FUNDS, result);
        syncHistories(HOT_FUNDS, result);
        return result;
    }

    /** 批量同步所有净值为0的基金净值（多线程并行，后台执行） */
    public SyncResult syncAllNavBatch() {
        SyncResult result = new SyncResult();

        List<Fund> pendingFunds = fundMapper.selectList(
            new LambdaQueryWrapper<Fund>()
                .select(Fund::getCode)
                .eq(Fund::getNav, BigDecimal.ZERO)
        );

        result.totalFunds = pendingFunds.size();
        log.info("批量同步净值开始，共 {} 只基金", result.totalFunds);
        if (pendingFunds.isEmpty()) return result;

        ExecutorService executor = Executors.newFixedThreadPool(BATCH_THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        for (Fund fund : pendingFunds) {
            String code = fund.getCode();
            executor.submit(() -> {
                try {
                    if (syncFundRealtime(code)) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.warn("批量同步被中断");
            Thread.currentThread().interrupt();
        }

        result.navSuccess = success.get();
        result.navFail = fail.get();
        log.info("批量净值同步完成: 成功 {}, 失败 {}", result.navSuccess, result.navFail);
        return result;
    }

    /** 从天天基金拉取全市场基金列表 */
    private int syncFundList(SyncResult result) {
        try {
            String raw = restTemplate.getForObject(FUND_LIST_URL, String.class);
            if (raw == null || raw.isEmpty()) {
                result.listFail = true;
                return 0;
            }
            // 提取 JS 数组内容
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end < 0) { result.listFail = true; return 0; }
            String arrJson = raw.substring(start, end + 1);

            // 解析顶层数组
            List<List<String>> items = parseFundListArray(arrJson);
            int inserted = 0;
            for (List<String> item : items) {
                if (item.size() < 4) continue;
                String code = item.get(0);
                String name = item.get(2);
                String type = item.get(3);

                Fund existing = fundMapper.selectOne(
                    new LambdaQueryWrapper<Fund>().eq(Fund::getCode, code)
                );
                if (existing == null) {
                    Fund fund = new Fund();
                    fund.setCode(code);
                    fund.setName(name);
                    fund.setType(type);
                    fund.setNav(BigDecimal.ZERO);
                    fund.setNavDate(LocalDate.now());
                    fund.setDayIncrease(BigDecimal.ZERO);
                    fund.setCompany("");
                    fundMapper.insert(fund);
                    inserted++;
                }
            }
            result.listInserted = inserted;
            return inserted;
        } catch (Exception e) {
            log.warn("获取全量基金列表失败: {}", e.getMessage());
            result.listFail = true;
            return 0;
        }
    }

    /** 批量同步净值 */
    private void syncNavs(List<String> codes, SyncResult result) {
        for (String code : codes) {
            try {
                if (syncFundRealtime(code)) result.navSuccess++;
                else result.navFail++;
            } catch (Exception e) {
                log.warn("同步 {} 净值失败: {}", code, e.getMessage());
                result.navFail++;
            }
        }
    }

    /** 批量同步历史净值 */
    private void syncHistories(List<String> codes, SyncResult result) {
        for (String code : codes) {
            try {
                int cnt = syncFundHistory(code);
                result.historySuccess += cnt;
            } catch (Exception e) {
                log.warn("同步 {} 历史净值失败: {}", code, e.getMessage());
                result.historyFail++;
            }
        }
    }

    /** 绕开 RestTemplate 的 MIME 校验，直接读取 URL 响应 */
    private String fetchRaw(String urlPattern, String code) {
        try {
            String resolved = urlPattern.replace("{code}", code);
            URL url = URI.create(resolved).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://fund.eastmoney.com/");
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("请求失败 {}: {}", code, e.getMessage());
            return null;
        }
    }

    /** 同步单只基金实时净值 */
    public boolean syncFundRealtime(String code) {
        String raw = fetchRaw(REALTIME_URL, code);
        if (raw == null) return false;
        if (raw == null || raw.isEmpty()) return false;

        String json = raw.trim();
        if (json.startsWith("jsonpgz(")) json = json.substring(8);
        if (json.endsWith(");")) json = json.substring(0, json.length() - 2);
        if (json.endsWith(")")) json = json.substring(0, json.length() - 1);

        Map<String, String> data = parseSimpleJson(json);
        String fundCode = data.get("fundcode");
        String name = data.get("name");
        String dwjz = data.get("dwjz");
        String jzrq = data.get("jzrq");
        String gszzl = data.get("gszzl");

        if (fundCode == null || dwjz == null) return false;

        Fund existing = fundMapper.selectOne(
            new LambdaQueryWrapper<Fund>().eq(Fund::getCode, fundCode)
        );
        Fund fund = existing != null ? existing : new Fund();
        fund.setCode(fundCode);
        if (name != null) fund.setName(name);
        try { fund.setNav(new BigDecimal(dwjz)); } catch (Exception e) { return false; }
        if (jzrq != null) try { fund.setNavDate(LocalDate.parse(jzrq)); } catch (Exception ignored) {}
        if (gszzl != null) try {
            fund.setDayIncrease(new BigDecimal(gszzl).divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP));
        } catch (Exception ignored) {}
        if (fund.getType() == null) fund.setType("混合型");
        if (fund.getCompany() == null) fund.setCompany("--");

        if (existing != null) fundMapper.updateById(fund);
        else fundMapper.insert(fund);
        return true;
    }

    /** 同步历史净值 */
    private int syncFundHistory(String code) {
        String raw = fetchRaw(HISTORY_URL, code);
        if (raw == null || raw.isEmpty()) return 0;

        String json = raw.trim();
        int startIdx = json.indexOf('(');
        int endIdx = json.lastIndexOf(')');
        if (startIdx >= 0 && endIdx > startIdx) {
            json = json.substring(startIdx + 1, endIdx);
        }

        int listStart = json.indexOf("\"LSJZList\":");
        if (listStart < 0) return 0;
        int arrStart = json.indexOf('[', listStart);
        int arrEnd = json.lastIndexOf(']');
        if (arrStart < 0 || arrEnd < 0) return 0;

        String arrJson = json.substring(arrStart, arrEnd + 1);
        List<Map<String, String>> items = parseJsonArray(arrJson);
        int saved = 0;
        for (Map<String, String> item : items) {
            String date = item.get("FSRQ");
            String nav = item.get("DWJZ");
            if (date == null || nav == null || nav.isEmpty()) continue;

            NavHistory existing = navHistoryMapper.selectOne(
                new LambdaQueryWrapper<NavHistory>()
                    .eq(NavHistory::getFundCode, code)
                    .eq(NavHistory::getDate, LocalDate.parse(date))
            );
            if (existing == null && saved < 20) {
                NavHistory h = new NavHistory();
                h.setFundCode(code);
                h.setNav(new BigDecimal(nav));
                h.setDate(LocalDate.parse(date));
                navHistoryMapper.insert(h);
                saved++;
            }
        }
        return saved;
    }

    /** 解析 JS 数组: [["code","name","type","pinyin"],["code",...]] */
    private List<List<String>> parseFundListArray(String json) {
        List<List<String>> result = new ArrayList<>();
        // 按 [" 模式匹配每个内层数组
        int i = 0;
        while (i < json.length()) {
            // 找 [" 开头
            int start = json.indexOf("[\"", i);
            if (start < 0) break;
            int end = json.indexOf(']', start);
            if (end < 0) break;
            String inner = json.substring(start + 1, end);
            i = end + 1;

            // 解析 CSV 字段: "code","shortPinyin","name","type","longPinyin"
            List<String> fields = new ArrayList<>();
            boolean inStr = false;
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < inner.length(); k++) {
                char c = inner.charAt(k);
                if (c == '"') { inStr = !inStr; continue; }
                if (c == ',' && !inStr) {
                    fields.add(sb.toString());
                    sb = new StringBuilder();
                    continue;
                }
                sb.append(c);
            }
            fields.add(sb.toString());
            if (fields.size() >= 3) {
                result.add(fields);
            }
        }
        return result;
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        boolean inString = false;
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    parseOneField(sb.toString(), result);
                    sb = new StringBuilder();
                    continue;
                }
            }
            sb.append(c);
        }
        parseOneField(sb.toString(), result);
        return result;
    }

    private void parseOneField(String pair, Map<String, String> result) {
        pair = pair.trim();
        int colon = pair.indexOf(':');
        if (colon < 0) return;
        String key = pair.substring(0, colon).trim().replaceAll("\"", "");
        String value = pair.substring(colon + 1).trim().replaceAll("\"", "");
        result.put(key, value);
    }

    private List<Map<String, String>> parseJsonArray(String arrJson) {
        List<Map<String, String>> list = new ArrayList<>();
        arrJson = arrJson.trim();
        if (arrJson.startsWith("[")) arrJson = arrJson.substring(1);
        if (arrJson.endsWith("]")) arrJson = arrJson.substring(0, arrJson.length() - 1);

        int i = 0;
        while (i < arrJson.length()) {
            while (i < arrJson.length() && arrJson.charAt(i) != '{') i++;
            if (i >= arrJson.length()) break;
            int depth = 0;
            int j = i;
            while (j < arrJson.length()) {
                char c = arrJson.charAt(j);
                if (c == '{') depth++;
                if (c == '}') depth--;
                if (depth == 0) break;
                j++;
            }
            String objJson = arrJson.substring(i, j + 1);
            list.add(parseSimpleJson(objJson));
            i = j + 1;
        }
        return list;
    }

    public static class SyncResult {
        public int totalFunds;
        public int listInserted;
        public boolean listFail;
        public int navSuccess;
        public int navFail;
        public int historySuccess;
        public int historyFail;

        public int getTotalFunds() { return totalFunds; }
        public int getListInserted() { return listInserted; }
        public boolean isListFail() { return listFail; }
        public int getNavSuccess() { return navSuccess; }
        public int getNavFail() { return navFail; }
        public int getHistorySuccess() { return historySuccess; }
        public int getHistoryFail() { return historyFail; }
    }
}

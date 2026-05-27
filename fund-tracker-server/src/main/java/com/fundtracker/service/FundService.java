package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.mapper.NavHistoryMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.NavHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundService {
    private static final Logger log = LoggerFactory.getLogger(FundService.class);
    private static final String FUND_OVERVIEW_URL = "https://fundf10.eastmoney.com/jbgk_%s.html";
    private static final String REALTIME_URL = "https://fundgz.1234567.com.cn/js/%s.js";

    private final FundMapper fundMapper;
    private final NavHistoryMapper navHistoryMapper;

    public FundService(FundMapper fundMapper, NavHistoryMapper navHistoryMapper) {
        this.fundMapper = fundMapper;
        this.navHistoryMapper = navHistoryMapper;
    }

    public IPage<Fund> listFunds(String keyword, String type, int page, int size) {
        LambdaQueryWrapper<Fund> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Fund::getName, keyword).or().like(Fund::getCode, keyword));
        } else {
            // 默认只展示有净值的基金，按日涨跌幅排序
            wrapper.gt(Fund::getNav, BigDecimal.ZERO)
                   .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                   .orderByDesc(Fund::getDayIncrease);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Fund::getType, type);
        }
        return fundMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Fund getFundByCode(String code) {
        Fund fund = fundMapper.selectOne(
            new LambdaQueryWrapper<Fund>().eq(Fund::getCode, code)
        );
        if (fund == null) return null;

        // 如果净值为0，尝试从天天基金获取实时净值
        if (fund.getNav() == null || fund.getNav().compareTo(BigDecimal.ZERO) == 0) {
            tryFetchRealtimeNav(fund);
        }
        // 如果公司信息缺失，从天天基金补全
        if (fund.getCompany() == null || fund.getCompany().isEmpty() || fund.getCompany().equals("--")) {
            enrichFundData(fund);
        }
        return fund;
    }

    public List<NavHistory> getNavHistory(String fundCode) {
        return navHistoryMapper.findByFundCode(fundCode);
    }

    public IPage<Fund> screenerQuery(String keyword, String type, String company,
                                      BigDecimal minNav, BigDecimal maxNav,
                                      BigDecimal minDayIncrease, BigDecimal maxDayIncrease,
                                      String minEstablishDate, int page, int size) {
        LambdaQueryWrapper<Fund> wrapper = new LambdaQueryWrapper<>();
        // 关键字：基金名称或代码模糊搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Fund::getName, keyword).or().like(Fund::getCode, keyword));
        }
        // 基金类型
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Fund::getType, type);
        }
        // 基金公司
        if (company != null && !company.isEmpty()) {
            wrapper.eq(Fund::getCompany, company);
        }
        // 最新净值范围
        if (minNav != null) {
            wrapper.ge(Fund::getNav, minNav);
        }
        if (maxNav != null) {
            wrapper.le(Fund::getNav, maxNav);
        }
        // 日涨跌范围
        if (minDayIncrease != null) {
            wrapper.ge(Fund::getDayIncrease, minDayIncrease);
        }
        if (maxDayIncrease != null) {
            wrapper.le(Fund::getDayIncrease, maxDayIncrease);
        }
        // 成立日期（在此日期之后成立）
        if (minEstablishDate != null && !minEstablishDate.isEmpty()) {
            wrapper.ge(Fund::getEstablishDate, LocalDate.parse(minEstablishDate));
        }
        // 默认按最新净值降序排序
        wrapper.orderByDesc(Fund::getNav);
        return fundMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public List<String> listCompanies() {
        return fundMapper.selectList(
            new LambdaQueryWrapper<Fund>()
                .select(Fund::getCompany)
                .isNotNull(Fund::getCompany)
                .ne(Fund::getCompany, "")
                .ne(Fund::getCompany, "--")
                .groupBy(Fund::getCompany)
        ).stream().map(Fund::getCompany).toList();
    }

    public List<String> listFundTypes() {
        return fundMapper.selectList(
            new LambdaQueryWrapper<Fund>().select(Fund::getType)
        ).stream().map(Fund::getType).distinct().toList();
    }

    /**
     * 从天天基金实时净值接口获取净值
     */
    private void tryFetchRealtimeNav(Fund fund) {
        try {
            String url = String.format(REALTIME_URL, fund.getCode());
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            String raw;
            try (InputStream is = conn.getInputStream()) {
                raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (raw == null || raw.isEmpty()) return;

            String json = raw.trim();
            if (json.startsWith("jsonpgz(")) json = json.substring(8);
            if (json.endsWith(");")) json = json.substring(0, json.length() - 2);
            if (json.endsWith(")")) json = json.substring(0, json.length() - 1);

            Map<String, String> data = parseSimpleJson(json);
            String dwjz = data.get("dwjz");
            String jzrq = data.get("jzrq");
            String gszzl = data.get("gszzl");

            if (dwjz != null) {
                fund.setNav(new BigDecimal(dwjz));
                if (jzrq != null) fund.setNavDate(LocalDate.parse(jzrq));
                if (gszzl != null) {
                    fund.setDayIncrease(new BigDecimal(gszzl).divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP));
                }
                fundMapper.updateById(fund);
                log.info("补全 {} 实时净值: {}", fund.getCode(), dwjz);
            }
        } catch (Exception e) {
            log.debug("获取 {} 实时净值失败: {}", fund.getCode(), e.getMessage());
        }
    }

    // ============ 基金经理 & 费率爬取 ============

    private static final String FUND_OVERVIEW = "https://fundf10.eastmoney.com/jbgk_%s.html";
    private static final String NAV_HISTORY_API = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=%s&pageIndex=%d&pageSize=%d";

    /**
     * 获取基金经理姓名
     */
    public String getFundManagerName(String fundCode) {
        try {
            String html = fetchHtml(String.format(FUND_OVERVIEW, fundCode));
            if (html == null) return null;

            // 匹配基金经理所在行的下一个链接中的文本
            Pattern p = Pattern.compile(
                "<th[^>]*>基金经理</th>\\s*<td[^>]*>\\s*<a[^>]*>([^<]+)</a>"
            );
            Matcher m = p.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
            // 如果没链接，直接取 td 文本
            p = Pattern.compile("<th[^>]*>基金经理</th>\\s*<td[^>]*>([^<]+)</td>");
            m = p.matcher(html);
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception e) {
            log.debug("获取基金经理失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取基金费率信息
     */
    public Map<String, Object> getFundFeeInfo(String fundCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String html = fetchHtml(String.format(FUND_OVERVIEW, fundCode));
            if (html == null) return result;

            result.put("managementFee", extractFee(html, "管理费率"));
            result.put("custodianFee", extractFee(html, "托管费率"));
            result.put("serviceFee", extractFee(html, "销售服务费率"));
            return result;
        } catch (Exception e) {
            log.debug("获取费率失败: {}", e.getMessage());
            return result;
        }
    }

    private String extractFee(String html, String label) {
        Pattern p = Pattern.compile(
            "<th[^>]*>" + Pattern.quote(label) + "</th>\\s*<td[^>]*>([^<]+)</td>"
        );
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1).trim().replace("&nbsp;", "") : null;
    }

    /**
     * 获取基金经理管理的基金列表（从经理详情页）
     */
    public Map<String, Object> getFundManagerDetail(String fundCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String overviewHtml = fetchHtml(String.format(FUND_OVERVIEW, fundCode));
            if (overviewHtml == null) return result;

            // 提取经理姓名
            Pattern nameP = Pattern.compile(
                "<th[^>]*>基金经理</th>\\s*<td[^>]*>\\s*<a[^>]*href=\"/manager/(\\d+)\\.html\"[^>]*>([^<]+)</a>"
            );
            Matcher nameM = nameP.matcher(overviewHtml);
            if (!nameM.find()) return result;

            String managerId = nameM.group(1).trim();
            String managerName = nameM.group(2).trim();
            result.put("managerName", managerName);
            result.put("managerId", managerId);

            // 获取经理详情页
            String detailHtml = fetchHtml("https://fund.eastmoney.com/manager/" + managerId + ".html");
            if (detailHtml != null) {
                // 从业时间
                Pattern careerP = Pattern.compile(
                    "<th[^>]*>任职时间</th>\\s*<td[^>]*>([^<]+)</td>"
                );
                Matcher careerM = careerP.matcher(detailHtml);
                if (careerM.find()) result.put("careerStart", careerM.group(1).trim());

                // 管理基金数
                Pattern fundCountP = Pattern.compile(
                    "<th[^>]*>管理基金数</th>\\s*<td[^>]*>([^<]+)</td>"
                );
                Matcher fcM = fundCountP.matcher(detailHtml);
                if (fcM.find()) result.put("fundCount", fcM.group(1).trim());

                // 最佳回报
                Pattern returnP = Pattern.compile(
                    "<th[^>]*>任职最佳回报</th>\\s*<td[^>]*>([^<]+)</td>"
                );
                Matcher rM = returnP.matcher(detailHtml);
                if (rM.find()) result.put("bestReturn", rM.group(1).trim());
            }

            return result;
        } catch (Exception e) {
            log.debug("获取经理详情失败: {}", e.getMessage());
            return result;
        }
    }

    /**
     * 从天天基金拉取历史净值（支持指定天数）
     */
    public List<NavHistory> fetchNavHistory(String fundCode, int days) {
        try {
            int pageSize = Math.min(days, 50); // API 限制 pageSize <= 50
            List<NavHistory> result = new ArrayList<>();
            int page = 1;

            while (result.size() < days) {
                String url = String.format(NAV_HISTORY_API, fundCode, page, pageSize);
                String raw = fetchUrl(url);
                if (raw == null) break;

                List<Map<String, String>> items = parseNavHistoryJson(raw);
                if (items == null || items.isEmpty()) break;

                for (Map<String, String> item : items) {
                    String date = item.get("FSRQ");
                    String nav = item.get("DWJZ");
                    if (date == null || nav == null || nav.isEmpty()) continue;
                    NavHistory h = new NavHistory();
                    h.setFundCode(fundCode);
                    h.setNav(new BigDecimal(nav));
                    h.setDate(LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE));

                    // 保存到数据库
                    try {
                        NavHistory existing = navHistoryMapper.selectOne(
                            new LambdaQueryWrapper<NavHistory>()
                                .eq(NavHistory::getFundCode, h.getFundCode())
                                .eq(NavHistory::getDate, h.getDate())
                        );
                        if (existing == null) {
                            navHistoryMapper.insert(h);
                        }
                    } catch (Exception ignored) {}

                    result.add(h);
                    if (result.size() >= days) break;
                }
                page++;
                // 最多查 20 页（约 400 条 = 20 个月，满足近1年/近3年计算）
                if (page > 20) break;
            }

            return result;
        } catch (Exception e) {
            log.debug("获取历史净值失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, String>> parseNavHistoryJson(String raw) {
        try {
            String json = raw.trim();
            // 兼容 JSONP 格式: callback(...)
            int startIdx = json.indexOf('(');
            int endIdx = json.lastIndexOf(')');
            if (startIdx >= 0 && endIdx > startIdx) {
                json = json.substring(startIdx + 1, endIdx);
            }
            // 用 Jackson 解析
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.get("Data");
            if (data == null || data.isNull()) return List.of();
            JsonNode list = data.get("LSJZList");
            if (list == null || !list.isArray()) return List.of();

            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode item : list) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("FSRQ", item.has("FSRQ") ? item.get("FSRQ").asText("") : "");
                map.put("DWJZ", item.has("DWJZ") ? item.get("DWJZ").asText("") : "");
                result.add(map);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String fetchHtml(String urlStr) {
        return fetchUrl(urlStr);
    }

    private String fetchUrl(String urlStr) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
            conn.setRequestProperty("Referer", "https://fund.eastmoney.com/");
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
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

    /**
     * 从天天基金基金概况页面抓取缺失的信息（基金管理人、成立日期）
     */
    private void enrichFundData(Fund fund) {
        try {
            String url = String.format(FUND_OVERVIEW_URL, fund.getCode());
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

            String html;
            try (InputStream is = conn.getInputStream()) {
                html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean updated = false;

            // 提取基金管理人
            Matcher companyMatcher = Pattern.compile(
                "<th>[^<]*基金管理人[^<]*</th>\\s*<td[^>]*>\\s*<a[^>]*>([^<]+)</a>"
            ).matcher(html);
            if (companyMatcher.find()) {
                fund.setCompany(companyMatcher.group(1).trim());
                updated = true;
            }

            // 提取成立日期/规模
            Matcher dateMatcher = Pattern.compile(
                "<th>[^<]*成立日期/规模[^<]*</th>\\s*<td[^>]*>([^<]*)</td>"
            ).matcher(html);
            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group(1).trim();
                Matcher d = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(dateStr);
                if (d.find() && fund.getEstablishDate() == null) {
                    fund.setEstablishDate(LocalDate.parse(d.group(1), DateTimeFormatter.ISO_LOCAL_DATE));
                    updated = true;
                }
            }

            if (updated) {
                fundMapper.updateById(fund);
                log.info("补全基金 {} 信息: 公司={}, 成立日={}", fund.getCode(), fund.getCompany(), fund.getEstablishDate());
            }
        } catch (Exception e) {
            log.debug("获取 {} 详情失败: {}", fund.getCode(), e.getMessage());
        }
    }
}

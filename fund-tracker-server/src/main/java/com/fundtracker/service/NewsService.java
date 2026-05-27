package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.model.entity.Fund;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String NEWS_URL = "https://finance.eastmoney.com/";
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000L; // 5 分钟

    private final FundMapper fundMapper;
    private final DeepSeekService deepSeekService;

    /** 内存缓存 */
    private NewsBriefing cachedBriefing;
    private long cacheTimestamp;

    public NewsService(FundMapper fundMapper, @Lazy DeepSeekService deepSeekService) {
        this.fundMapper = fundMapper;
        this.deepSeekService = deepSeekService;
    }

    /**
     * 获取今日基金市场简报（含缓存，5分钟有效）
     */
    public NewsBriefing getMarketBriefing() {
        // 缓存命中
        if (cachedBriefing != null
                && System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS) {
            return cachedBriefing;
        }

        // 1. 查询基金市场数据
        List<Fund> top10 = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .isNotNull(Fund::getDayIncrease)
                        .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                        .orderByDesc(Fund::getDayIncrease)
                        .last("LIMIT 10")
        );

        List<Fund> bottom10 = fundMapper.selectList(
                new LambdaQueryWrapper<Fund>()
                        .isNotNull(Fund::getDayIncrease)
                        .ne(Fund::getDayIncrease, BigDecimal.ZERO)
                        .orderByAsc(Fund::getDayIncrease)
                        .last("LIMIT 10")
        );

        long upCount = fundMapper.selectCount(
                new LambdaQueryWrapper<Fund>()
                        .isNotNull(Fund::getDayIncrease)
                        .gt(Fund::getDayIncrease, BigDecimal.ZERO)
        );
        long downCount = fundMapper.selectCount(
                new LambdaQueryWrapper<Fund>()
                        .isNotNull(Fund::getDayIncrease)
                        .lt(Fund::getDayIncrease, BigDecimal.ZERO)
        );

        // 2. 尝试抓取新闻
        List<NewsItem> scrapedItems = scrapeNews();

        NewsBriefing briefing;
        if (!scrapedItems.isEmpty()) {
            // 抓取成功：用 DeepSeek 生成摘要
            String summary = generateSummary(top10, bottom10, upCount, downCount, scrapedItems);
            briefing = new NewsBriefing("今日基金市场简报",
                    summary, scrapedItems, LocalDate.now(), "DeepSeek AI");
        } else {
            // 抓取失败：用 DeepSeek 生成完整市场简报
            log.info("新闻抓取为空，使用 DeepSeek 生成市场简报");
            String fullBriefing = generateFallbackBriefing(top10, bottom10, upCount, downCount);
            briefing = new NewsBriefing("今日基金市场简报",
                    fullBriefing, Collections.emptyList(), LocalDate.now(), "DeepSeek AI");
        }

        // 更新缓存
        cachedBriefing = briefing;
        cacheTimestamp = System.currentTimeMillis();
        return briefing;
    }

    /**
     * 从东方财富财经首页抓取新闻
     */
    private List<NewsItem> scrapeNews() {
        try {
            URI uri = URI.create(NEWS_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://finance.eastmoney.com/");

            String html;
            try (InputStream is = conn.getInputStream()) {
                html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (html == null || html.isEmpty()) {
                log.warn("抓取新闻页面返回为空");
                return Collections.emptyList();
            }

            // 解析新闻链接：<a ... title="标题" ... href="...">
            List<NewsItem> items = new ArrayList<>();
            Pattern pattern = Pattern.compile(
                    "<a[^>]*href=\"(https?://[^\"]+)\"[^>]*title=\"([^\"]+)\"[^>]*>"
            );
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String href = matcher.group(1).trim();
                String title = matcher.group(2).trim();

                if (title.isEmpty() || title.length() < 5) continue;
                // 去重（按 URL）
                boolean exists = items.stream().anyMatch(n -> n.getUrl().equals(href));
                if (!exists) {
                    items.add(new NewsItem(title, href, "", ""));
                }
                if (items.size() >= 15) break;
            }

            log.info("抓取到 {} 条新闻", items.size());
            return items;
        } catch (Exception e) {
            log.warn("抓取东方财富新闻失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 新闻抓取成功时，结合市场数据调用 DeepSeek 生成摘要
     */
    private String generateSummary(List<Fund> top10, List<Fund> bottom10,
                                    long upCount, long downCount,
                                    List<NewsItem> scrapedItems) {
        String top5Str = top10.stream()
                .limit(5)
                .map(f -> String.format("%s(%s) %.2f%%",
                        f.getName(), f.getCode(),
                        f.getDayIncrease().multiply(BigDecimal.valueOf(100))))
                .collect(Collectors.joining("、"));

        String bottom5Str = bottom10.stream()
                .limit(5)
                .map(f -> String.format("%s(%s) %.2f%%",
                        f.getName(), f.getCode(),
                        f.getDayIncrease().multiply(BigDecimal.valueOf(100))))
                .collect(Collectors.joining("、"));

        String newsListStr = scrapedItems.stream()
                .map(NewsItem::getTitle)
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                根据以下今日基金市场数据和新闻，生成一份300字以内的市场简报：
                【市场数据】
                上涨基金数：%d，下跌基金数：%d
                涨幅前5：%s
                跌幅前5：%s
                【今日重要新闻】
                %s
                请包含：市场总体表现、热点板块、投资建议。
                """, upCount, downCount, top5Str, bottom5Str, newsListStr);

        return deepSeekService.callDeepSeekWithPrompt(prompt);
    }

    /**
     * 新闻抓取失败时的回退方案：仅使用市场数据让 DeepSeek 生成简报
     */
    private String generateFallbackBriefing(List<Fund> top10, List<Fund> bottom10,
                                             long upCount, long downCount) {
        String top5Str = top10.stream()
                .limit(5)
                .map(f -> String.format("%s(%s) %.2f%%",
                        f.getName(), f.getCode(),
                        f.getDayIncrease().multiply(BigDecimal.valueOf(100))))
                .collect(Collectors.joining("、"));

        String bottom5Str = bottom10.stream()
                .limit(5)
                .map(f -> String.format("%s(%s) %.2f%%",
                        f.getName(), f.getCode(),
                        f.getDayIncrease().multiply(BigDecimal.valueOf(100))))
                .collect(Collectors.joining("、"));

        String prompt = String.format("""
                请根据以下基金市场数据，生成一份简明扼要的今日基金市场简报（300字以内）：
                今日上涨基金数：%d，下跌基金数：%d
                涨幅前5：%s
                跌幅前5：%s
                请包含：市场总体表现、热点板块、投资建议。
                """, upCount, downCount, top5Str, bottom5Str);

        return deepSeekService.callDeepSeekWithPrompt(prompt);
    }

    // ==================== 内部类 ====================

    public static class NewsBriefing {
        private String title;
        private String summary;
        private List<NewsItem> newsItems;
        private LocalDate date;
        private String source;

        public NewsBriefing() {}

        public NewsBriefing(String title, String summary, List<NewsItem> newsItems,
                            LocalDate date, String source) {
            this.title = title;
            this.summary = summary;
            this.newsItems = newsItems;
            this.date = date;
            this.source = source;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<NewsItem> getNewsItems() { return newsItems; }
        public void setNewsItems(List<NewsItem> newsItems) { this.newsItems = newsItems; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class NewsItem {
        private String title;
        private String url;
        private String date;
        private String summary;

        public NewsItem() {}

        public NewsItem(String title, String url, String date, String summary) {
            this.title = title;
            this.url = url;
            this.date = date;
            this.summary = summary;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}

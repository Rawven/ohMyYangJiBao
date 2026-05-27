package com.fundtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class IndexValuationService {
    private static final Logger log = LoggerFactory.getLogger(IndexValuationService.class);

    private static final List<IndexDef> INDEXES = List.of(
        new IndexDef("sh000001", "上证指数", "000001", 10, 25),
        new IndexDef("sh000300", "沪深300", "000300", 10, 22),
        new IndexDef("sz399001", "深证成指", "399001", 15, 50),
        new IndexDef("sz399006", "创业板指", "399006", 25, 80),
        new IndexDef("sh000688", "科创50", "000688", 30, 150),
        new IndexDef("sh000016", "上证50", "000016", 8, 18),
        new IndexDef("sh000905", "中证500", "000905", 15, 50)
    );

    private final List<IndexValuation> cache = new CopyOnWriteArrayList<>();
    private volatile long lastFetch = 0;
    private static final long CACHE_TTL = 60000; // 1 minute

    public List<IndexValuation> getValuations() {
        if (System.currentTimeMillis() - lastFetch < CACHE_TTL && !cache.isEmpty()) {
            return cache;
        }
        return fetchValuations();
    }

    private List<IndexValuation> fetchValuations() {
        List<IndexValuation> result = new ArrayList<>();
        try {
            String q = String.join(",", INDEXES.stream().map(i -> i.code).toList());
            URI uri = URI.create("https://qt.gtimg.cn/q=" + q);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            String text;
            try (InputStream is = conn.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                // Tencent API uses GBK encoding
                text = new String(bytes, Charset.forName("GBK"));
            }

            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] parts = line.split("~");
                if (parts.length < 70) continue;

                String rawCode = parts[2];
                String name;
                double price, changePct, pe, high52w, low52w, amplitude, turnover;
                try {
                    name = parts[1];
                    price = parseDouble(parts[3]);
                    changePct = parseDouble(parts[32]);
                    pe = parseDouble(parts[39]);
                    high52w = parseDouble(parts[67]);
                    low52w = parseDouble(parts[68]);
                    amplitude = parseDouble(parts[43]);
                    turnover = parseDouble(parts[38]);
                } catch (Exception e) {
                    continue;
                }

                if (price <= 0) continue;

                // Map back to our index definition
                IndexDef def = INDEXES.stream()
                    .filter(d -> d.exchangeCode.equals(rawCode) || d.code.equals(rawCode))
                    .findFirst().orElse(null);
                if (def == null) continue;

                // 使用参考 PE 范围计算估值的百分位和水平
                double pePercentile = 50.0;
                if (pe > 0) {
                    double range = def.lowPeRef - def.highPeRef;
                    if (range < 0) range = -range;
                    if (range > 0) {
                        pePercentile = (pe - def.lowPeRef) / range * 100;
                        pePercentile = Math.max(0, Math.min(100, pePercentile));
                    }
                }

                String level;
                if (pe <= 0) level = "--";
                else if (pePercentile < 20) level = "低估";
                else if (pePercentile < 40) level = "偏低";
                else if (pePercentile < 60) level = "适中";
                else if (pePercentile < 80) level = "偏高";
                else level = "高估";

                result.add(new IndexValuation(
                    def.displayName, def.exchangeCode, price, changePct,
                    pe > 0 ? pe : null, amplitude, turnover,
                    high52w, low52w, pePercentile, level
                ));
            }

            if (!result.isEmpty()) {
                cache.clear();
                cache.addAll(result);
                lastFetch = System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.error("获取指数估值失败: {}", e.getMessage());
            if (!cache.isEmpty()) return cache;
        }
        return result;
    }

    private double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private record IndexDef(String code, String displayName, String exchangeCode, double lowPeRef, double highPeRef) {}

    public record IndexValuation(
        String name,
        String code,
        double price,
        double changePct,
        Double pe,
        double amplitude,
        double turnover,
        double high52w,
        double low52w,
        double pePercentile,
        String level
    ) {}
}

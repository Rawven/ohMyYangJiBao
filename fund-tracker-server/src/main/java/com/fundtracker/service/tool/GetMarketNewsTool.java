package com.fundtracker.service.tool;

import com.fundtracker.service.NewsService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetMarketNewsTool implements AiTool {

    private final NewsService newsService;

    public GetMarketNewsTool(NewsService newsService) {
        this.newsService = newsService;
    }

    @Override
    public String getName() {
        return "get_market_news";
    }

    @Override
    public String getDescription() {
        return "获取今日市场新闻简报";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            NewsService.NewsBriefing briefing = newsService.getMarketBriefing();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", briefing.getTitle());
            result.put("summary", briefing.getSummary());
            result.put("date", briefing.getDate() != null ? briefing.getDate().toString() : null);
            result.put("source", briefing.getSource());

            if (briefing.getNewsItems() != null) {
                List<Map<String, Object>> items = briefing.getNewsItems().stream().map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", n.getTitle());
                    m.put("url", n.getUrl());
                    m.put("date", n.getDate());
                    m.put("summary", n.getSummary());
                    return m;
                }).toList();
                result.put("newsItems", items);
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "获取市场新闻失败: " + e.getMessage());
        }
    }
}

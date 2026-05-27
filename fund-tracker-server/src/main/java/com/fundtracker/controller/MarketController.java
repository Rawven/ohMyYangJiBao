package com.fundtracker.controller;

import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.FundFlowService;
import com.fundtracker.service.FundFlowService.FundFlowItem;
import com.fundtracker.service.IndexValuationService;
import com.fundtracker.service.IndexValuationService.IndexValuation;
import com.fundtracker.service.IndustryAnalysisService;
import com.fundtracker.service.IndustryAnalysisService.IndustryAnalysis;
import com.fundtracker.service.NewsService;
import com.fundtracker.service.NewsService.NewsBriefing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    private final NewsService newsService;
    private final IndustryAnalysisService industryAnalysisService;
    private final FundFlowService fundFlowService;
    private final IndexValuationService indexValuationService;

    public MarketController(NewsService newsService,
                             IndustryAnalysisService industryAnalysisService,
                             FundFlowService fundFlowService,
                             IndexValuationService indexValuationService) {
        this.newsService = newsService;
        this.industryAnalysisService = industryAnalysisService;
        this.fundFlowService = fundFlowService;
        this.indexValuationService = indexValuationService;
    }

    /**
     * 获取今日基金市场简报（新闻 + AI 摘要）
     */
    @GetMapping("/news")
    public ApiResponse<NewsBriefing> getMarketNews() {
        try {
            NewsBriefing briefing = newsService.getMarketBriefing();
            return ApiResponse.success(briefing);
        } catch (Exception e) {
            log.error("获取市场简报失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取市场简报失败: " + e.getMessage());
        }
    }

    /**
     * 获取基金持仓行业板块分析
     */
    @GetMapping("/industry")
    public ApiResponse<IndustryAnalysis> getIndustryAnalysis(
            @RequestParam(required = false) String industry) {
        try {
            IndustryAnalysis analysis = industryAnalysisService.getIndustryAnalysis(industry);
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            log.error("获取行业分析失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取行业分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取基金资金流向列表（机构/个人持有比例、净申购等）
     */
    @GetMapping("/fund-flow")
    public ApiResponse<List<FundFlowItem>> getFundFlow() {
        try {
            List<FundFlowItem> list = fundFlowService.getFundFlowList();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("获取资金流向失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取资金流向失败: " + e.getMessage());
        }
    }

    /**
     * 获取指数估值（PE、估值水平等）
     */
    @GetMapping("/index-valuation")
    public ApiResponse<List<IndexValuation>> getIndexValuation() {
        try {
            List<IndexValuation> list = indexValuationService.getValuations();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("获取指数估值失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取指数估值失败: " + e.getMessage());
        }
    }
}

package com.fundtracker.controller;

import com.fundtracker.model.dto.AnalysisDTO;
import com.fundtracker.model.entity.FundHolding;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DeepSeekService deepSeekService;
    private final FundHoldingService fundHoldingService;
    private final FundService fundService;
    private final FundFlowService fundFlowService;

    public AnalysisController(AnalysisService analysisService,
                              DeepSeekService deepSeekService,
                              FundHoldingService fundHoldingService,
                              FundService fundService,
                              FundFlowService fundFlowService) {
        this.analysisService = analysisService;
        this.deepSeekService = deepSeekService;
        this.fundHoldingService = fundHoldingService;
        this.fundService = fundService;
        this.fundFlowService = fundFlowService;
    }

    @GetMapping
    public ApiResponse<AnalysisDTO> getAnalysis() {
        return ApiResponse.success(analysisService.getAnalysis());
    }

    /**
     * LLM 分析基金持仓
     */
    @GetMapping("/fund/{code}")
    public ApiResponse<String> analyzeFund(@PathVariable String code) {
        var fund = fundService.getFundByCode(code);
        if (fund == null) {
            return ApiResponse.error(404, "基金不存在");
        }
        List<FundHolding> holdings = fundHoldingService.getHoldings(code);
        if (holdings.isEmpty()) {
            return ApiResponse.error(404, "暂无持仓数据");
        }

        String reportDate = holdings.get(0).getReportDate() != null
                ? holdings.get(0).getReportDate().toString() : "未知";

        String analysis = deepSeekService.analyzeHoldings(
                code, fund.getName(), fund.getType(), reportDate, holdings);
        return ApiResponse.success(analysis);
    }

    /**
     * LLM 分析基金资金流向（主力机构流入流出）
     */
    @GetMapping("/fund/{code}/flow")
    public ApiResponse<String> analyzeFlow(@PathVariable String code) {
        var fund = fundService.getFundByCode(code);
        if (fund == null) {
            return ApiResponse.error(404, "基金不存在");
        }
        String analysis = fundFlowService.analyzeFlow(
                code, fund.getName(), fund.getType());
        return ApiResponse.success(analysis);
    }
}

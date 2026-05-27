package com.fundtracker.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.FundHolding;
import com.fundtracker.model.entity.NavHistory;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.FundHoldingService;
import com.fundtracker.service.FundService;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/funds")
public class FundController {
    private final FundService fundService;
    private final FundHoldingService fundHoldingService;

    public FundController(FundService fundService, FundHoldingService fundHoldingService) {
        this.fundService = fundService;
        this.fundHoldingService = fundHoldingService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listFunds(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<Fund> pageResult = fundService.listFunds(keyword, type, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("items", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return ApiResponse.success(result);
    }

    @GetMapping("/{code}")
    public ApiResponse<Fund> getFund(@PathVariable String code) {
        Fund fund = fundService.getFundByCode(code);
        if (fund == null) {
            return ApiResponse.error(404, "基金不存在");
        }
        return ApiResponse.success(fund);
    }

    @GetMapping("/{code}/nav")
    public ApiResponse<List<NavHistory>> getNavHistory(@PathVariable String code) {
        return ApiResponse.success(fundService.getNavHistory(code));
    }

    @GetMapping("/types")
    public ApiResponse<List<String>> listTypes() {
        return ApiResponse.success(fundService.listFundTypes());
    }

    @GetMapping("/companies")
    public ApiResponse<List<String>> listCompanies() {
        return ApiResponse.success(fundService.listCompanies());
    }

    @GetMapping("/{code}/holdings")
    public ApiResponse<List<FundHolding>> getHoldings(@PathVariable String code) {
        return ApiResponse.success(fundHoldingService.getHoldings(code));
    }

    /**
     * 多基金净值走势对比
     */
    @GetMapping("/compare-nav")
    public ApiResponse<Map<String, List<NavHistory>>> compareNavHistory(
            @RequestParam("codes") String codes) {
        String[] codeArr = codes.split(",");
        Map<String, List<NavHistory>> result = new java.util.LinkedHashMap<>();
        for (String code : codeArr) {
            code = code.trim();
            if (code.isEmpty()) continue;
            result.put(code, fundService.getNavHistory(code));
        }
        return ApiResponse.success(result);
    }

    /**
     * 基金对比：同时返回多只基金的详情+持仓
     */
    @GetMapping("/compare")
    public ApiResponse<List<Map<String, Object>>> compareFunds(
            @RequestParam("codes") String codes) {
        String[] codeArr = codes.split(",");
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (String code : codeArr) {
            code = code.trim();
            if (code.isEmpty()) continue;
            Fund fund = fundService.getFundByCode(code);
            if (fund == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("fundCode", fund.getCode());
            item.put("fundName", fund.getName());
            item.put("fundType", fund.getType());
            item.put("nav", fund.getNav());
            item.put("navDate", fund.getNavDate());
            item.put("dayIncrease", fund.getDayIncrease());
            item.put("company", fund.getCompany());
            // 持仓前三
            List<FundHolding> holdings = fundHoldingService.getHoldings(code);
            item.put("topHoldings",
                holdings.stream().limit(3).map(h ->
                    h.getStockName() + "(" + h.getHoldRatio() + "%)"
                ).toList()
            );
            result.add(item);
        }
        return ApiResponse.success(result);
    }
}

package com.fundtracker.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.FundService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/funds")
public class FundScreenerController {

    private final FundService fundService;

    public FundScreenerController(FundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping("/screener")
    public ApiResponse<Map<String, Object>> screenerQuery(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) BigDecimal minNav,
            @RequestParam(required = false) BigDecimal maxNav,
            @RequestParam(required = false) BigDecimal minDayIncrease,
            @RequestParam(required = false) BigDecimal maxDayIncrease,
            @RequestParam(required = false) String minEstablishDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<Fund> pageResult = fundService.screenerQuery(keyword, type, company,
                minNav, maxNav, minDayIncrease, maxDayIncrease, minEstablishDate, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("items", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return ApiResponse.success(result);
    }
}

package com.fundtracker.controller;

import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.PhotoParseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PhotoParseService photoParseService;

    public PortfolioController(PhotoParseService photoParseService) {
        this.photoParseService = photoParseService;
    }

    /**
     * 上传持仓截图，返回 OCR + AI 解析结果（不写库）
     */
    @PostMapping("/parse-photo")
    public ApiResponse<List<Map<String, Object>>> parsePhoto(@RequestParam("file") MultipartFile file) {
        try {
            List<Map<String, Object>> result = photoParseService.parseImage(file);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "解析截图失败: " + e.getMessage());
        }
    }

    /**
     * 确认替换持仓
     */
    @PostMapping("/replace-holdings")
    public ApiResponse<List<Map<String, Object>>> replaceHoldings(@RequestBody List<Map<String, Object>> holdings) {
        try {
            List<Map<String, Object>> result = photoParseService.replaceHoldings(holdings);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "替换持仓失败: " + e.getMessage());
        }
    }
}

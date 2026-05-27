package com.fundtracker.controller;

import com.fundtracker.model.dto.HoldingDTO;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.HoldingService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {
    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping
    public ApiResponse<List<HoldingDTO>> listHoldings() {
        return ApiResponse.success(holdingService.listHoldingDTOs());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateHolding(@PathVariable Long id, @RequestBody Holding holding) {
        holdingService.updateHolding(id, holding);
        return ApiResponse.success(null);
    }
}

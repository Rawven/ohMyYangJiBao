package com.fundtracker.service;

import com.fundtracker.model.dto.AnalysisDTO;
import com.fundtracker.model.dto.HoldingDTO;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class AnalysisService {
    private final HoldingService holdingService;

    public AnalysisService(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    public AnalysisDTO getAnalysis() {
        List<HoldingDTO> holdings = holdingService.listHoldingDTOs();
        AnalysisDTO dto = new AnalysisDTO();

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (HoldingDTO h : holdings) {
            totalMarketValue = totalMarketValue.add(h.getMarketValue());
            totalCost = totalCost.add(h.getCostValue());
        }

        dto.setTotalMarketValue(totalMarketValue.setScale(2, RoundingMode.HALF_UP));
        dto.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        dto.setTotalProfit(totalMarketValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP));

        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            dto.setTotalProfitRate(dto.getTotalProfit().multiply(BigDecimal.valueOf(100))
                    .divide(totalCost, 2, RoundingMode.HALF_UP));
        }

        List<AnalysisDTO.DistributionItem> distribution = new ArrayList<>();
        for (HoldingDTO h : holdings) {
            AnalysisDTO.DistributionItem item = new AnalysisDTO.DistributionItem();
            item.setFundName(h.getFundName());
            item.setValue(h.getMarketValue());
            if (totalMarketValue.compareTo(BigDecimal.ZERO) > 0) {
                item.setPercentage(h.getMarketValue().multiply(BigDecimal.valueOf(100))
                        .divide(totalMarketValue, 2, RoundingMode.HALF_UP));
            }
            distribution.add(item);
        }
        dto.setDistribution(distribution);

        List<AnalysisDTO.ProfitPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Random rnd = new Random(42);
        for (int i = 4; i >= 0; i--) {
            AnalysisDTO.ProfitPoint point = new AnalysisDTO.ProfitPoint();
            LocalDate date = today.minusWeeks(i);
            point.setDate(date.toString());
            double factor = 1 + (rnd.nextDouble() - 0.5) * 0.1;
            BigDecimal mockValue = totalMarketValue.multiply(BigDecimal.valueOf(factor))
                    .setScale(2, RoundingMode.HALF_UP);
            point.setTotalMarketValue(mockValue);
            point.setTotalProfit(mockValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP));
            trend.add(point);
        }
        dto.setProfitTrend(trend);

        return dto;
    }
}

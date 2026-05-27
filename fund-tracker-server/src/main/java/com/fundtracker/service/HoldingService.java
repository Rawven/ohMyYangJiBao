package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.HoldingMapper;
import com.fundtracker.model.dto.HoldingDTO;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.Holding;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class HoldingService {
    private final HoldingMapper holdingMapper;
    private final FundService fundService;

    public HoldingService(HoldingMapper holdingMapper, FundService fundService) {
        this.holdingMapper = holdingMapper;
        this.fundService = fundService;
    }

    public List<HoldingDTO> listHoldingDTOs() {
        List<Holding> holdings = holdingMapper.selectList(new LambdaQueryWrapper<>());
        List<HoldingDTO> result = new ArrayList<>();
        for (Holding h : holdings) {
            Fund fund = fundService.getFundByCode(h.getFundCode());
            HoldingDTO dto = new HoldingDTO();
            dto.setId(h.getId());
            dto.setFundCode(h.getFundCode());
            dto.setFundName(h.getFundName());
            dto.setFundType(fund != null ? fund.getType() : "");
            dto.setShares(h.getShares());
            dto.setCostNav(h.getCostNav());
            dto.setCurrentNav(fund != null && fund.getNav() != null ? fund.getNav() : h.getCostNav());

            BigDecimal currentNav = dto.getCurrentNav();
            dto.setMarketValue(h.getShares().multiply(currentNav).setScale(2, RoundingMode.HALF_UP));
            BigDecimal costValue = h.getShares().multiply(h.getCostNav()).setScale(2, RoundingMode.HALF_UP);
            dto.setCostValue(costValue);
            dto.setProfit(dto.getMarketValue().subtract(costValue).setScale(2, RoundingMode.HALF_UP));
            if (costValue.compareTo(BigDecimal.ZERO) > 0) {
                dto.setProfitRate(dto.getProfit().multiply(BigDecimal.valueOf(100))
                        .divide(costValue, 2, RoundingMode.HALF_UP));
            } else {
                dto.setProfitRate(BigDecimal.ZERO);
            }
            result.add(dto);
        }
        return result;
    }

    public void updateHolding(Long id, Holding holding) {
        holding.setId(id);
        holdingMapper.updateById(holding);
    }
}

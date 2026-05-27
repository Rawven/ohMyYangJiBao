package com.fundtracker.model.dto;

import java.math.BigDecimal;

public class HoldingDTO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private BigDecimal shares;
    private BigDecimal costNav;
    private BigDecimal currentNav;
    private BigDecimal marketValue;
    private BigDecimal costValue;
    private BigDecimal profit;
    private BigDecimal profitRate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }
    public BigDecimal getShares() { return shares; }
    public void setShares(BigDecimal shares) { this.shares = shares; }
    public BigDecimal getCostNav() { return costNav; }
    public void setCostNav(BigDecimal costNav) { this.costNav = costNav; }
    public BigDecimal getCurrentNav() { return currentNav; }
    public void setCurrentNav(BigDecimal currentNav) { this.currentNav = currentNav; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getCostValue() { return costValue; }
    public void setCostValue(BigDecimal costValue) { this.costValue = costValue; }
    public BigDecimal getProfit() { return profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }
    public BigDecimal getProfitRate() { return profitRate; }
    public void setProfitRate(BigDecimal profitRate) { this.profitRate = profitRate; }
}

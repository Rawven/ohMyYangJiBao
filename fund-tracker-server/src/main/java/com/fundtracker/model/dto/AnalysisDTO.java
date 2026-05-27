package com.fundtracker.model.dto;

import java.math.BigDecimal;
import java.util.List;

public class AnalysisDTO {
    private BigDecimal totalMarketValue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private List<ProfitPoint> profitTrend;
    private List<DistributionItem> distribution;

    public BigDecimal getTotalMarketValue() { return totalMarketValue; }
    public void setTotalMarketValue(BigDecimal totalMarketValue) { this.totalMarketValue = totalMarketValue; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }
    public BigDecimal getTotalProfitRate() { return totalProfitRate; }
    public void setTotalProfitRate(BigDecimal totalProfitRate) { this.totalProfitRate = totalProfitRate; }
    public List<ProfitPoint> getProfitTrend() { return profitTrend; }
    public void setProfitTrend(List<ProfitPoint> profitTrend) { this.profitTrend = profitTrend; }
    public List<DistributionItem> getDistribution() { return distribution; }
    public void setDistribution(List<DistributionItem> distribution) { this.distribution = distribution; }

    public static class ProfitPoint {
        private String date;
        private BigDecimal totalProfit;
        private BigDecimal totalMarketValue;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public BigDecimal getTotalProfit() { return totalProfit; }
        public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }
        public BigDecimal getTotalMarketValue() { return totalMarketValue; }
        public void setTotalMarketValue(BigDecimal totalMarketValue) { this.totalMarketValue = totalMarketValue; }
    }

    public static class DistributionItem {
        private String fundName;
        private BigDecimal value;
        private BigDecimal percentage;

        public String getFundName() { return fundName; }
        public void setFundName(String fundName) { this.fundName = fundName; }
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        public BigDecimal getPercentage() { return percentage; }
        public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    }
}

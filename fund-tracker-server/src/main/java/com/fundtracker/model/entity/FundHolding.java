package com.fundtracker.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FundHolding {
    private Long id;
    private String fundCode;
    private String stockName;
    private String stockCode;
    private BigDecimal holdRatio;
    private BigDecimal changeRatio;
    private LocalDate reportDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public BigDecimal getHoldRatio() { return holdRatio; }
    public void setHoldRatio(BigDecimal holdRatio) { this.holdRatio = holdRatio; }
    public BigDecimal getChangeRatio() { return changeRatio; }
    public void setChangeRatio(BigDecimal changeRatio) { this.changeRatio = changeRatio; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
}

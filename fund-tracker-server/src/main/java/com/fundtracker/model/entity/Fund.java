package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("fund")
public class Fund {
    @TableId
    private Long id;
    private String code;
    private String name;
    private String type;
    private BigDecimal nav;
    private LocalDate navDate;

    @TableField("day_increase")
    private BigDecimal dayIncrease;

    private LocalDate establishDate;
    private String company;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }
    public LocalDate getNavDate() { return navDate; }
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }
    public BigDecimal getDayIncrease() { return dayIncrease; }
    public void setDayIncrease(BigDecimal dayIncrease) { this.dayIncrease = dayIncrease; }
    public LocalDate getEstablishDate() { return establishDate; }
    public void setEstablishDate(LocalDate establishDate) { this.establishDate = establishDate; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
}

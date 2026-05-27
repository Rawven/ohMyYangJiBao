# 基金跟踪应用 (养基宝) 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个类似"养基宝"的公募基金投资跟踪 Web 应用，支持基金自选、持仓管理、交易记录和收益分析

**Architecture:** 前后端分离架构 — React + TypeScript 前端通过 REST API 与 Spring Boot 后端通信，数据存储在 H2 数据库中，内置 Mock 种子数据脱网可用

**Tech Stack:** Spring Boot 3 / MyBatis-Plus / H2 / React 18 / TypeScript / Vite / Ant Design 5 / ECharts / React Query / Zustand

---

### Task 1: 后端项目脚手架

**Files:**
- Create: `fund-tracker-server/pom.xml`
- Create: `fund-tracker-server/src/main/resources/application.yml`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/FundTrackerApplication.java`

- [ ] **Step 1: 创建 Maven 项目结构和 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.4</version>
    </parent>
    <groupId>com.fundtracker</groupId>
    <artifactId>fund-tracker-server</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.7</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 application.yml**

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:h2:file:./data/fundtracker;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

- [ ] **Step 3: 创建启动类**

```java
package com.fundtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FundTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FundTrackerApplication.class, args);
    }
}
```

- [ ] **Step 4: 验证启动**

Run: `cd fund-tracker-server && mvn spring-boot:run`
Expected: Spring Boot 启动成功，无报错

---

### Task 2: 数据库 Schema + 种子数据

**Files:**
- Create: `fund-tracker-server/src/main/resources/schema.sql`
- Create: `fund-tracker-server/src/main/resources/data.sql`

- [ ] **Step 1: 创建 schema.sql**

```sql
DROP TABLE IF EXISTS transaction;
DROP TABLE IF EXISTS holding;
DROP TABLE IF EXISTS nav_history;
DROP TABLE IF EXISTS fund;

CREATE TABLE fund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    nav_date DATE NOT NULL,
    day_increase DECIMAL(6, 4) DEFAULT 0,
    establish_date DATE,
    company VARCHAR(100)
);

CREATE TABLE nav_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    date DATE NOT NULL,
    UNIQUE (fund_code, date)
);

CREATE TABLE holding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    shares DECIMAL(14, 2) NOT NULL,
    cost_nav DECIMAL(10, 4) NOT NULL,
    buy_date DATE NOT NULL
);

CREATE TABLE transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(10) NOT NULL,
    type VARCHAR(4) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    nav DECIMAL(10, 4) NOT NULL,
    shares DECIMAL(14, 2) NOT NULL,
    transaction_date DATETIME NOT NULL,
    note VARCHAR(500)
);
```

- [ ] **Step 2: 创建 data.sql（种子数据）**

```sql
-- 基金数据
INSERT INTO fund (code, name, type, nav, nav_date, day_increase, establish_date, company)
VALUES
('110011', '易方达中小盘混合', '混合型', 4.8523, '2026-05-22', 0.0123, '2008-06-19', '易方达基金'),
('005827', '中欧时代先锋股票', '股票型', 1.6789, '2026-05-22', -0.0087, '2015-11-03', '中欧基金'),
('008283', '华安科技动力混合', '混合型', 3.2156, '2026-05-22', 0.0056, '2011-12-20', '华安基金'),
('110003', '易方达上证50增强', '指数型', 2.1458, '2026-05-22', 0.0032, '2004-03-22', '易方达基金'),
('000001', '华夏成长混合', '混合型', 1.2345, '2026-05-22', 0.0021, '2001-12-18', '华夏基金'),
('002001', '华夏回报混合', '混合型', 1.5678, '2026-05-22', 0.0015, '2003-09-05', '华夏基金'),
('070001', '嘉实成长收益混合', '混合型', 2.3456, '2026-05-22', -0.0034, '2002-11-05', '嘉实基金'),
('040001', '华安创新混合', '混合型', 1.1234, '2026-05-22', 0.0045, '2001-09-21', '华安基金'),
('160105', '南方积极配置混合', '混合型', 1.8765, '2026-05-22', 0.0067, '2004-10-14', '南方基金'),
('233001', '大摩基础行业混合', '混合型', 0.9876, '2026-05-22', -0.0012, '2004-03-26', '摩根士丹利基金'),
('260108', '景顺长城新兴成长', '混合型', 2.5678, '2026-05-22', 0.0089, '2006-06-28', '景顺长城基金'),
('163402', '兴全趋势投资混合', '混合型', 1.4567, '2026-05-22', 0.0034, '2005-11-03', '兴证全球基金');

-- 净值历史数据（简化：仅生成近一个月的每周数据）
INSERT INTO nav_history (fund_code, nav, date) VALUES
('110011', 4.8231, '2026-04-22'), ('110011', 4.8456, '2026-04-29'), ('110011', 4.8321, '2026-05-06'),
('110011', 4.8612, '2026-05-13'), ('110011', 4.8523, '2026-05-22'),
('005827', 1.6543, '2026-04-22'), ('005827', 1.6789, '2026-04-29'), ('005827', 1.6654, '2026-05-06'),
('005827', 1.6876, '2026-05-13'), ('005827', 1.6789, '2026-05-22'),
('260108', 2.5342, '2026-04-22'), ('260108', 2.5567, '2026-04-29'), ('260108', 2.5432, '2026-05-06'),
('260108', 2.5765, '2026-05-13'), ('260108', 2.5678, '2026-05-22');

-- 持仓数据
INSERT INTO holding (fund_code, fund_name, shares, cost_nav, buy_date) VALUES
('110011', '易方达中小盘混合', 5000.00, 4.5231, '2025-10-15'),
('005827', '中欧时代先锋股票', 8000.00, 1.5234, '2025-08-20'),
('260108', '景顺长城新兴成长', 3000.00, 2.3456, '2026-01-10');

-- 交易记录
INSERT INTO transaction (fund_code, type, amount, nav, shares, transaction_date, note) VALUES
('110011', 'BUY', 22615.50, 4.5231, 5000.00, '2025-10-15 09:30:00', '初次建仓'),
('005827', 'BUY', 12187.20, 1.5234, 8000.00, '2025-08-20 10:00:00', '定投买入'),
('260108', 'BUY', 7036.80, 2.3456, 3000.00, '2026-01-10 14:00:00', '逢低买入'),
('110011', 'BUY', 9650.00, 4.8250, 2000.00, '2026-03-05 09:45:00', '加仓'),
('005827', 'SELL', 5000.00, 1.6500, 3030.30, '2026-04-12 11:00:00', '部分止盈');
```

- [ ] **Step 3: 验证数据库初始化**

Run: `cd fund-tracker-server && mvn spring-boot:run`
检查启动日志中 H2 SQL 初始化是否成功，访问 `http://localhost:8080/h2-console` 确认表已创建且有数据

---

### Task 3: 后端 Entity + Mapper 层

**Files:**
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/entity/Fund.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/entity/NavHistory.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/entity/Holding.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/entity/Transaction.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/vo/ApiResponse.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/mapper/FundMapper.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/mapper/NavHistoryMapper.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/mapper/HoldingMapper.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/mapper/TransactionMapper.java`

- [ ] **Step 1: 创建 Fund 实体**

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
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
}
```

- [ ] **Step 2: 创建 NavHistory 实体**

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("nav_history")
public class NavHistory {
    @TableId
    private Long id;
    private String fundCode;
    private BigDecimal nav;
    private LocalDate date;
}
```

- [ ] **Step 3: 创建 Holding 实体**

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("holding")
public class Holding {
    @TableId
    private Long id;
    private String fundCode;
    private String fundName;
    private BigDecimal shares;
    private BigDecimal costNav;
    private LocalDate buyDate;
}
```

- [ ] **Step 4: 创建 Transaction 实体**

```java
package com.fundtracker.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transaction")
public class Transaction {
    @TableId
    private Long id;
    private String fundCode;
    private String type; // BUY / SELL
    private BigDecimal amount;
    private BigDecimal nav;
    private BigDecimal shares;
    private LocalDateTime transactionDate;
    private String note;
}
```

- [ ] **Step 5: 创建统一响应类**

```java
package com.fundtracker.model.vo;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

- [ ] **Step 6: 创建 Mapper 接口**

```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Fund;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FundMapper extends BaseMapper<Fund> {}
```

```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.NavHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface NavHistoryMapper extends BaseMapper<NavHistory> {
    @Select("SELECT * FROM nav_history WHERE fund_code = #{fundCode} ORDER BY date ASC")
    List<NavHistory> findByFundCode(@Param("fundCode") String fundCode);
}
```

```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Holding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HoldingMapper extends BaseMapper<Holding> {}
```

```java
package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {}
```

---

### Task 4: 后端 Service 层

**Files:**
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/dto/HoldingDTO.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/model/dto/AnalysisDTO.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/service/FundService.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/service/HoldingService.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/service/TransactionService.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/service/AnalysisService.java`

- [ ] **Step 1: 创建 DTO 类**

```java
package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HoldingDTO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private BigDecimal shares;
    private BigDecimal costNav;
    private BigDecimal currentNav;
    private BigDecimal marketValue;   // 市值 = shares * currentNav
    private BigDecimal costValue;     // 成本 = shares * costNav
    private BigDecimal profit;        // 盈亏 = marketValue - costValue
    private BigDecimal profitRate;    // 收益率 = profit / costValue * 100
}
```

```java
package com.fundtracker.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisDTO {
    private BigDecimal totalMarketValue;
    private BigDecimal totalCost;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private List<ProfitPoint> profitTrend;
    private List<DistributionItem> distribution;

    @Data
    public static class ProfitPoint {
        private String date;
        private BigDecimal totalProfit;
        private BigDecimal totalMarketValue;
    }

    @Data
    public static class DistributionItem {
        private String fundName;
        private BigDecimal value;
        private BigDecimal percentage;
    }
}
```

- [ ] **Step 2: 创建 FundService**

```java
package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.mapper.NavHistoryMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.NavHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FundService {
    private final FundMapper fundMapper;
    private final NavHistoryMapper navHistoryMapper;

    public List<Fund> listFunds(String keyword, String type) {
        LambdaQueryWrapper<Fund> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Fund::getName, keyword).or().like(Fund::getCode, keyword);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Fund::getType, type);
        }
        return fundMapper.selectList(wrapper);
    }

    public Fund getFundByCode(String code) {
        return fundMapper.selectOne(
            new LambdaQueryWrapper<Fund>().eq(Fund::getCode, code)
        );
    }

    public List<NavHistory> getNavHistory(String fundCode) {
        return navHistoryMapper.findByFundCode(fundCode);
    }

    public List<String> listFundTypes() {
        return fundMapper.selectList(
            new LambdaQueryWrapper<Fund>().select("DISTINCT type")
        ).stream().map(Fund::getType).distinct().toList();
    }
}
```

- [ ] **Step 3: 创建 HoldingService**

```java
package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.HoldingMapper;
import com.fundtracker.model.dto.HoldingDTO;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.Holding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldingService {
    private final HoldingMapper holdingMapper;
    private final FundService fundService;

    public List<HoldingDTO> listHoldingDTOs() {
        List<Holding> holdings = holdingMapper.selectList(null);
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
            dto.setCurrentNav(fund != null ? fund.getNav() : h.getCostNav());

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
```

- [ ] **Step 4: 创建 TransactionService**

```java
package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.TransactionMapper;
import com.fundtracker.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionMapper transactionMapper;

    public List<Transaction> listTransactions(String fundCode) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        if (fundCode != null && !fundCode.isEmpty()) {
            wrapper.eq(Transaction::getFundCode, fundCode);
        }
        wrapper.orderByDesc(Transaction::getTransactionDate);
        return transactionMapper.selectList(wrapper);
    }

    public Transaction addTransaction(Transaction transaction) {
        transactionMapper.insert(transaction);
        return transaction;
    }

    public void deleteTransaction(Long id) {
        transactionMapper.deleteById(id);
    }
}
```

- [ ] **Step 5: 创建 AnalysisService**

```java
package com.fundtracker.service;

import com.fundtracker.model.dto.AnalysisDTO;
import com.fundtracker.model.dto.HoldingDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private final HoldingService holdingService;

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

        // 持仓分布
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

        // 模拟收益趋势（简化：基于当前持仓构造近5期数据）
        List<AnalysisDTO.ProfitPoint> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 4; i >= 0; i--) {
            AnalysisDTO.ProfitPoint point = new AnalysisDTO.ProfitPoint();
            LocalDate date = today.minusWeeks(i);
            point.setDate(date.toString());
            // 模拟：每周市值波动
            double factor = 1 + (Math.random() - 0.5) * 0.1;
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
```

---

### Task 5: 后端 Controller + CORS 配置

**Files:**
- Create: `fund-tracker-server/src/main/java/com/fundtracker/controller/FundController.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/controller/HoldingController.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/controller/TransactionController.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/controller/AnalysisController.java`
- Create: `fund-tracker-server/src/main/java/com/fundtracker/config/CorsConfig.java`

- [ ] **Step 1: 创建 CORS 配置**

```java
package com.fundtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **Step 2: 创建 FundController**

```java
package com.fundtracker.controller;

import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.NavHistory;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {
    private final FundService fundService;

    @GetMapping
    public ApiResponse<List<Fund>> listFunds(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type) {
        return ApiResponse.success(fundService.listFunds(keyword, type));
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
}
```

- [ ] **Step 3: 创建 HoldingController**

```java
package com.fundtracker.controller;

import com.fundtracker.model.dto.HoldingDTO;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.HoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {
    private final HoldingService holdingService;

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
```

- [ ] **Step 4: 创建 TransactionController**

```java
package com.fundtracker.controller;

import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public ApiResponse<List<Transaction>> listTransactions(
            @RequestParam(required = false) String fundCode) {
        return ApiResponse.success(transactionService.listTransactions(fundCode));
    }

    @PostMapping
    public ApiResponse<Transaction> addTransaction(@RequestBody Transaction transaction) {
        return ApiResponse.success(transactionService.addTransaction(transaction));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 5: 创建 AnalysisController**

```java
package com.fundtracker.controller;

import com.fundtracker.model.dto.AnalysisDTO;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;

    @GetMapping
    public ApiResponse<AnalysisDTO> getAnalysis() {
        return ApiResponse.success(analysisService.getAnalysis());
    }
}
```

- [ ] **Step 6: 验证后端 API**

Run: `cd fund-tracker-server && mvn spring-boot:run`
然后测试 API：`curl http://localhost:8080/api/funds`
Expected: 返回包含基金列表的 JSON，code=200

---

### Task 6: 前端项目脚手架

**Files:**
- Create: `fund-tracker-web/package.json`
- Create: `fund-tracker-web/vite.config.ts`
- Create: `fund-tracker-web/tsconfig.json`
- Create: `fund-tracker-web/tsconfig.node.json`
- Create: `fund-tracker-web/index.html`
- Create: `fund-tracker-web/src/main.tsx`
- Create: `fund-tracker-web/src/App.tsx`
- Create: `fund-tracker-web/src/vite-env.d.ts`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "fund-tracker-web",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.0",
    "@ant-design/icons": "^5.4.0",
    "antd": "^5.20.0",
    "echarts": "^5.5.1",
    "echarts-for-react": "^3.0.2",
    "@tanstack/react-query": "^5.51.0",
    "zustand": "^4.5.4",
    "axios": "^1.7.2",
    "dayjs": "^1.11.12"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.4.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 3: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": false,
    "noUnusedParameters": false,
    "noFallthroughCasesInSwitch": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: 创建 tsconfig.node.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>基金跟踪</title>
</head>
<body>
  <div id="root"></div>
  <script type="module" src="/src/main.tsx"></script>
</body>
</html>
```

- [ ] **Step 6: 创建 vite-env.d.ts**

```typescript
/// <reference types="vite/client" />
```

- [ ] **Step 7: 创建 main.tsx**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ConfigProvider>
    </QueryClientProvider>
  </React.StrictMode>,
)
```

- [ ] **Step 8: 创建 App.tsx（路由入口）**

```tsx
import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import FundMarket from './pages/FundMarket'
import FundDetail from './pages/FundDetail'
import Portfolio from './pages/Portfolio'
import Transactions from './pages/Transactions'
import Analysis from './pages/Analysis'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="funds" element={<FundMarket />} />
        <Route path="funds/:code" element={<FundDetail />} />
        <Route path="portfolio" element={<Portfolio />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="analysis" element={<Analysis />} />
      </Route>
    </Routes>
  )
}
```

- [ ] **Step 9: 安装依赖并验证启动**

Run: `cd fund-tracker-web && npm install`
Run: `cd fund-tracker-web && npm run build`
Expected: Build 成功无错误

---

### Task 7: 前端 Types + API 层 + Store

**Files:**
- Create: `fund-tracker-web/src/types/index.ts`
- Create: `fund-tracker-web/src/api/client.ts`
- Create: `fund-tracker-web/src/api/fund.ts`
- Create: `fund-tracker-web/src/api/holding.ts`
- Create: `fund-tracker-web/src/api/transaction.ts`
- Create: `fund-tracker-web/src/api/analysis.ts`
- Create: `fund-tracker-web/src/store/uiStore.ts`

- [ ] **Step 1: 创建类型定义**

```typescript
export interface Fund {
  id: number
  code: string
  name: string
  type: string
  nav: number
  navDate: string
  dayIncrease: number
  establishDate: string
  company: string
}

export interface NavHistory {
  id: number
  fundCode: string
  nav: number
  date: string
}

export interface HoldingDTO {
  id: number
  fundCode: string
  fundName: string
  fundType: string
  shares: number
  costNav: number
  currentNav: number
  marketValue: number
  costValue: number
  profit: number
  profitRate: number
}

export interface Transaction {
  id: number
  fundCode: string
  type: 'BUY' | 'SELL'
  amount: number
  nav: number
  shares: number
  transactionDate: string
  note: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface AnalysisData {
  totalMarketValue: number
  totalCost: number
  totalProfit: number
  totalProfitRate: number
  profitTrend: ProfitPoint[]
  distribution: DistributionItem[]
}

export interface ProfitPoint {
  date: string
  totalProfit: number
  totalMarketValue: number
}

export interface DistributionItem {
  fundName: string
  value: number
  percentage: number
}
```

- [ ] **Step 2: 创建 API 客户端**

```typescript
import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

client.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default client
```

- [ ] **Step 3: 创建 fund API**

```typescript
import client from './client'
import type { ApiResponse, Fund, NavHistory } from '../types'

export async function fetchFunds(keyword?: string, type?: string): Promise<Fund[]> {
  const params: Record<string, string> = {}
  if (keyword) params.keyword = keyword
  if (type) params.type = type
  const res = await client.get<any, ApiResponse<Fund[]>>('/funds', { params })
  return res.data
}

export async function fetchFundDetail(code: string): Promise<Fund> {
  const res = await client.get<any, ApiResponse<Fund>>(`/funds/${code}`)
  return res.data
}

export async function fetchNavHistory(code: string): Promise<NavHistory[]> {
  const res = await client.get<any, ApiResponse<NavHistory[]>>(`/funds/${code}/nav`)
  return res.data
}

export async function fetchFundTypes(): Promise<string[]> {
  const res = await client.get<any, ApiResponse<string[]>>('/funds/types')
  return res.data
}
```

- [ ] **Step 4: 创建 holding API**

```typescript
import client from './client'
import type { ApiResponse, HoldingDTO } from '../types'

export async function fetchHoldings(): Promise<HoldingDTO[]> {
  const res = await client.get<any, ApiResponse<HoldingDTO[]>>('/holdings')
  return res.data
}
```

- [ ] **Step 5: 创建 transaction API**

```typescript
import client from './client'
import type { ApiResponse, Transaction } from '../types'

export async function fetchTransactions(fundCode?: string): Promise<Transaction[]> {
  const params: Record<string, string> = {}
  if (fundCode) params.fundCode = fundCode
  const res = await client.get<any, ApiResponse<Transaction[]>>('/transactions', { params })
  return res.data
}

export async function addTransaction(data: Partial<Transaction>): Promise<Transaction> {
  const res = await client.post<any, ApiResponse<Transaction>>('/transactions', data)
  return res.data
}

export async function deleteTransaction(id: number): Promise<void> {
  await client.delete(`/transactions/${id}`)
}
```

- [ ] **Step 6: 创建 analysis API**

```typescript
import client from './client'
import type { ApiResponse, AnalysisData } from '../types'

export async function fetchAnalysis(): Promise<AnalysisData> {
  const res = await client.get<any, ApiResponse<AnalysisData>>('/analysis')
  return res.data
}
```

- [ ] **Step 7: 创建 Zustand Store**

```typescript
import { create } from 'zustand'

interface UIState {
  sidebarCollapsed: boolean
  toggleSidebar: () => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarCollapsed: false,
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}))
```

---

### Task 8: 前端 Layout 组件

**Files:**
- Create: `fund-tracker-web/src/components/Layout.tsx`
- Create: `fund-tracker-web/src/utils/format.ts`

- [ ] **Step 1: 创建格式化工具**

```typescript
export function formatMoney(value: number): string {
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatPercent(value: number): string {
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.slice(0, 10)
}

export function formatDateTime(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.slice(0, 19).replace('T', ' ')
}
```

- [ ] **Step 2: 创建 Layout 组件**

```tsx
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout as AntLayout, Menu } from 'antd'
import {
  DashboardOutlined,
  FundOutlined,
  WalletOutlined,
  SwapOutlined,
  PieChartOutlined,
} from '@ant-design/icons'
import { useUIStore } from '../store/uiStore'

const { Sider, Content } = AntLayout

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '总览' },
  { key: '/funds', icon: <FundOutlined />, label: '基金市场' },
  { key: '/portfolio', icon: <WalletOutlined />, label: '我的持仓' },
  { key: '/transactions', icon: <SwapOutlined />, label: '交易记录' },
  { key: '/analysis', icon: <PieChartOutlined />, label: '收益分析' },
]

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const collapsed = useUIStore((s) => s.sidebarCollapsed)

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={() => useUIStore.getState().toggleSidebar()}
        theme="light"
        style={{ borderRight: '1px solid #f0f0f0' }}
      >
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: collapsed ? 14 : 18 }}>
          {collapsed ? '基' : '基金跟踪'}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Content style={{ padding: 24, background: '#f5f5f5' }}>
        <Outlet />
      </Content>
    </AntLayout>
  )
}
```

---

### Task 9: 前端页面 — Dashboard 总览 + 基金市场

**Files:**
- Create: `fund-tracker-web/src/pages/Dashboard.tsx`
- Create: `fund-tracker-web/src/pages/FundMarket.tsx`

- [ ] **Step 1: 创建 Dashboard 页面**

```tsx
import { useQuery } from '@tanstack/react-query'
import { Row, Col, Card, Statistic, Table, Spin, Alert } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined, WalletOutlined, RiseOutlined } from '@ant-design/icons'
import { fetchAnalysis } from '../api/analysis'
import { fetchHoldings } from '../api/holding'
import { formatMoney, formatPercent } from '../utils/format'
import type { HoldingDTO } from '../types'
import { useNavigate } from 'react-router-dom'

export default function Dashboard() {
  const navigate = useNavigate()
  const { data: analysis, isLoading: loading1, isError: err1 } = useQuery({
    queryKey: ['analysis'],
    queryFn: fetchAnalysis,
  })
  const { data: holdings, isLoading: loading2 } = useQuery({
    queryKey: ['holdings'],
    queryFn: fetchHoldings,
  })

  if (loading1 || loading2) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (err1) return <Alert type="error" message="加载数据失败" banner />

  const columns = [
    { title: '基金名称', dataIndex: 'fundName', key: 'fundName' },
    {
      title: '市值', dataIndex: 'marketValue', key: 'marketValue',
      render: (v: number) => `¥${formatMoney(v)}`,
    },
    {
      title: '盈亏', dataIndex: 'profit', key: 'profit',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? '#f5222d' : '#52c41a' }}>
          {v >= 0 ? '+' : ''}{formatMoney(v)}
        </span>
      ),
    },
    {
      title: '收益率', dataIndex: 'profitRate', key: 'profitRate',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? '#f5222d' : '#52c41a' }}>
          {formatPercent(v)}
        </span>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card><Statistic title="持仓总市值" value={analysis?.totalMarketValue || 0} precision={2} prefix="¥" prefix={<WalletOutlined />} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="总成本" value={analysis?.totalCost || 0} precision={2} prefix="¥" /></Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总盈亏"
              value={analysis?.totalProfit || 0}
              precision={2}
              prefix={analysis?.totalProfit && analysis.totalProfit >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              valueStyle={{ color: analysis?.totalProfit && analysis.totalProfit >= 0 ? '#f5222d' : '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总收益率"
              value={analysis?.totalProfitRate || 0}
              precision={2}
              suffix="%"
              prefix={<RiseOutlined />}
              valueStyle={{ color: analysis?.totalProfitRate && analysis.totalProfitRate >= 0 ? '#f5222d' : '#52c41a' }}
            />
          </Card>
        </Col>
      </Row>
      <Card title="持仓概览">
        <Table
          dataSource={holdings || []}
          columns={columns}
          rowKey="id"
          pagination={false}
          onRow={(record: HoldingDTO) => ({
            onClick: () => navigate(`/funds/${record.fundCode}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: 创建 FundMarket 页面**

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Table, Input, Select, Tag, Spin, Alert } from 'antd'
import { SearchOutlined, StarOutlined, StarFilled } from '@ant-design/icons'
import { fetchFunds, fetchFundTypes } from '../api/fund'
import { useNavigate } from 'react-router-dom'
import { formatMoney, formatPercent } from '../utils/format'
import type { Fund } from '../types'

export default function FundMarket() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [watchlist, setWatchlist] = useState<Set<string>>(new Set(['110011', '005827', '260108']))

  const { data: funds, isLoading } = useQuery({
    queryKey: ['funds', keyword, typeFilter],
    queryFn: () => fetchFunds(keyword || undefined, typeFilter || undefined),
  })

  const { data: types } = useQuery({
    queryKey: ['fundTypes'],
    queryFn: fetchFundTypes,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />

  const toggleWatchlist = (code: string) => {
    setWatchlist((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  const columns = [
    {
      title: '自选',
      key: 'watch',
      width: 60,
      render: (_: unknown, record: Fund) => (
        <span onClick={(e) => { e.stopPropagation(); toggleWatchlist(record.code) }} style={{ cursor: 'pointer' }}>
          {watchlist.has(record.code) ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
        </span>
      ),
    },
    { title: '基金代码', dataIndex: 'code', key: 'code', width: 100 },
    { title: '基金名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型', dataIndex: 'type', key: 'type',
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '最新净值', dataIndex: 'nav', key: 'nav',
      render: (v: number) => formatMoney(v),
    },
    { title: '净值日期', dataIndex: 'navDate', key: 'navDate', render: (v: string) => v?.slice(5) },
    {
      title: '日涨跌', dataIndex: 'dayIncrease', key: 'dayIncrease',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? '#f5222d' : '#52c41a', fontWeight: 'bold' }}>
          {(v * 100).toFixed(2)}%
        </span>
      ),
    },
    { title: '基金公司', dataIndex: 'company', key: 'company' },
  ]

  return (
    <Card title="基金市场" extra={
      <div style={{ display: 'flex', gap: 8 }}>
        <Input
          placeholder="搜索基金名称/代码"
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 200 }}
          allowClear
        />
        <Select
          placeholder="基金类型"
          value={typeFilter || undefined}
          onChange={(v) => setTypeFilter(v || '')}
          allowClear
          style={{ width: 130 }}
          options={(types || []).map((t) => ({ label: t, value: t }))}
        />
      </div>
    }>
      <Table
        dataSource={funds || []}
        columns={columns}
        rowKey="id"
        onRow={(record) => ({
          onClick: () => navigate(`/funds/${record.code}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{ pageSize: 10, showSizeChanger: false }}
      />
    </Card>
  )
}
```

---

### Task 10: 前端页面 — 基金详情

**Files:**
- Create: `fund-tracker-web/src/pages/FundDetail.tsx`
- Create: `fund-tracker-web/src/components/NavChart.tsx`

- [ ] **Step 1: 创建 NavChart 组件（净值走势图）**

```tsx
import ReactEChartsCore from 'echarts-for-react'
import type { NavHistory } from '../types'

interface Props {
  data: NavHistory[]
}

export default function NavChart({ data }: Props) {
  const dates = data.map((d) => d.date?.slice(5))
  const values = data.map((d) => d.nav)

  const option = {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category' as const, data: dates },
    yAxis: { type: 'value' as const, name: '净值' },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      lineStyle: { color: '#1890ff', width: 2 },
      areaStyle: { color: 'rgba(24, 144, 255, 0.1)' },
      showSymbol: false,
    }],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
```

- [ ] **Step 2: 创建 FundDetail 页面**

```tsx
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Card, Descriptions, Tag, Spin, Alert, Row, Col, Statistic } from 'antd'
import { fetchFundDetail, fetchNavHistory } from '../api/fund'
import NavChart from '../components/NavChart'
import { formatMoney, formatPercent } from '../utils/format'

export default function FundDetail() {
  const { code } = useParams<{ code: string }>()

  const { data: fund, isLoading: loading1, isError: err1 } = useQuery({
    queryKey: ['fund', code],
    queryFn: () => fetchFundDetail(code!),
    enabled: !!code,
  })

  const { data: navHistory, isLoading: loading2 } = useQuery({
    queryKey: ['navHistory', code],
    queryFn: () => fetchNavHistory(code!),
    enabled: !!code,
  })

  if (loading1 || loading2) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (err1 || !fund) return <Alert type="error" message="基金不存在" banner />

  return (
    <div>
      <Card title={`${fund.name}（${fund.code}）`} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="最新净值" value={fund.nav} precision={4} /></Col>
          <Col span={6}>
            <Statistic
              title="日涨跌"
              value={(fund.dayIncrease * 100).toFixed(2)}
              suffix="%"
              valueStyle={{ color: fund.dayIncrease >= 0 ? '#f5222d' : '#52c41a' }}
            />
          </Col>
          <Col span={6}><Statistic title="净值日期" value={fund.navDate} /></Col>
          <Col span={6}><Statistic title="基金公司" value={fund.company} /></Col>
        </Row>
      </Card>
      <Card title="净值走势">
        {navHistory && navHistory.length > 0 ? <NavChart data={navHistory} /> : <Alert message="暂无净值数据" type="info" />}
      </Card>
      <Card title="基本信息" style={{ marginTop: 16 }}>
        <Descriptions column={2}>
          <Descriptions.Item label="基金代码">{fund.code}</Descriptions.Item>
          <Descriptions.Item label="基金名称">{fund.name}</Descriptions.Item>
          <Descriptions.Item label="基金类型"><Tag>{fund.type}</Tag></Descriptions.Item>
          <Descriptions.Item label="成立日期">{fund.establishDate}</Descriptions.Item>
          <Descriptions.Item label="基金公司">{fund.company}</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  )
}
```

---

### Task 11: 前端页面 — 我的持仓 + 交易记录

**Files:**
- Create: `fund-tracker-web/src/pages/Portfolio.tsx`
- Create: `fund-tracker-web/src/pages/Transactions.tsx`

- [ ] **Step 1: 创建 Portfolio 页面**

```tsx
import { useQuery } from '@tanstack/react-query'
import { Card, Table, Spin, Alert } from 'antd'
import { fetchHoldings } from '../api/holding'
import { formatMoney, formatPercent } from '../utils/format'
import { useNavigate } from 'react-router-dom'
import type { HoldingDTO } from '../types'

export default function Portfolio() {
  const navigate = useNavigate()
  const { data: holdings, isLoading, isError } = useQuery({
    queryKey: ['holdings'],
    queryFn: fetchHoldings,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载持仓失败" banner />

  const columns = [
    { title: '基金名称', dataIndex: 'fundName', key: 'fundName' },
    { title: '持有份额', dataIndex: 'shares', key: 'shares', render: (v: number) => v.toLocaleString() },
    { title: '成本净值', dataIndex: 'costNav', key: 'costNav', render: (v: number) => formatMoney(v) },
    { title: '当前净值', dataIndex: 'currentNav', key: 'currentNav', render: (v: number) => formatMoney(v) },
    { title: '市值', dataIndex: 'marketValue', key: 'marketValue', render: (v: number) => `¥${formatMoney(v)}` },
    { title: '成本金额', dataIndex: 'costValue', key: 'costValue', render: (v: number) => `¥${formatMoney(v)}` },
    {
      title: '盈亏', dataIndex: 'profit', key: 'profit',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? '#f5222d' : '#52c41a', fontWeight: 'bold' }}>
          {v >= 0 ? '+' : ''}¥{formatMoney(v)}
        </span>
      ),
    },
    {
      title: '收益率', dataIndex: 'profitRate', key: 'profitRate',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? '#f5222d' : '#52c41a', fontWeight: 'bold' }}>
          {formatPercent(v)}
        </span>
      ),
    },
  ]

  return (
    <Card title="我的持仓">
      <Table
        dataSource={holdings || []}
        columns={columns}
        rowKey="id"
        pagination={false}
        onRow={(record) => ({
          onClick: () => navigate(`/funds/${record.fundCode}`),
          style: { cursor: 'pointer' },
        })}
      />
    </Card>
  )
}
```

- [ ] **Step 2: 创建 Transactions 页面**

```tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Card, Table, Button, Modal, Form, Input, Select, DatePicker, InputNumber, message, Space, Tag, Spin, Alert } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { fetchTransactions, addTransaction, deleteTransaction } from '../api/transaction'
import { fetchFunds } from '../api/fund'
import { formatMoney, formatDateTime } from '../utils/format'
import type { Transaction } from '../types'
import dayjs from 'dayjs'

export default function Transactions() {
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  const { data: transactions, isLoading, isError } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => fetchTransactions(),
  })

  const { data: funds } = useQuery({
    queryKey: ['funds'],
    queryFn: () => fetchFunds(),
  })

  const addMutation = useMutation({
    mutationFn: addTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['holdings'] })
      queryClient.invalidateQueries({ queryKey: ['analysis'] })
      message.success('添加成功')
      setModalOpen(false)
      form.resetFields()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['holdings'] })
      queryClient.invalidateQueries({ queryKey: ['analysis'] })
      message.success('删除成功')
    },
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载交易记录失败" banner />

  const handleAdd = () => {
    const values = form.getFieldsValue()
    const selectedFund = funds?.find((f) => f.code === values.fundCode)
    addMutation.mutate({
      fundCode: values.fundCode,
      type: values.type,
      amount: values.amount,
      nav: values.nav,
      shares: values.shares,
      transactionDate: values.transactionDate.format('YYYY-MM-DD HH:mm:ss'),
      note: values.note || '',
    })
  }

  const columns = [
    { title: '基金代码', dataIndex: 'fundCode', key: 'fundCode' },
    {
      title: '类型', dataIndex: 'type', key: 'type',
      render: (v: string) => (
        <Tag color={v === 'BUY' ? 'red' : 'green'}>{v === 'BUY' ? '买入' : '卖出'}</Tag>
      ),
    },
    { title: '金额', dataIndex: 'amount', key: 'amount', render: (v: number) => `¥${formatMoney(v)}` },
    { title: '净值', dataIndex: 'nav', key: 'nav', render: (v: number) => formatMoney(v) },
    { title: '份额', dataIndex: 'shares', key: 'shares', render: (v: number) => v.toLocaleString() },
    { title: '时间', dataIndex: 'transactionDate', key: 'transactionDate', render: (v: string) => formatDateTime(v) },
    { title: '备注', dataIndex: 'note', key: 'note' },
    {
      title: '操作', key: 'action',
      render: (_: unknown, record: Transaction) => (
        <Button type="link" danger onClick={() => deleteMutation.mutate(record.id)}>删除</Button>
      ),
    },
  ]

  return (
    <Card title="交易记录" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新增交易</Button>}>
      <Table columns={columns} dataSource={transactions || []} rowKey="id" pagination={{ pageSize: 10 }} />
      <Modal title="新增交易" open={modalOpen} onOk={handleAdd} onCancel={() => setModalOpen(false)} confirmLoading={addMutation.isPending}>
        <Form form={form} layout="vertical">
          <Form.Item name="fundCode" label="基金" rules={[{ required: true }]}>
            <Select showSearch placeholder="选择基金" optionFilterProp="label"
              options={(funds || []).map((f) => ({ label: `${f.code} - ${f.name}`, value: f.code }))}
            />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={[{ label: '买入', value: 'BUY' }, { label: '卖出', value: 'SELL' }]} />
          </Form.Item>
          <Form.Item name="amount" label="金额" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} precision={2} min={0} />
          </Form.Item>
          <Form.Item name="nav" label="净值" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} precision={4} min={0} />
          </Form.Item>
          <Form.Item name="shares" label="份额" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} precision={2} min={0} />
          </Form.Item>
          <Form.Item name="transactionDate" label="交易时间" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
```

---

### Task 12: 前端页面 — 收益分析

**Files:**
- Create: `fund-tracker-web/src/pages/Analysis.tsx`
- Create: `fund-tracker-web/src/components/ProfitChart.tsx`
- Create: `fund-tracker-web/src/components/DistributionChart.tsx`

- [ ] **Step 1: 创建 ProfitChart 组件**

```tsx
import ReactEChartsCore from 'echarts-for-react'
import type { ProfitPoint } from '../types'

interface Props {
  data: ProfitPoint[]
}

export default function ProfitChart({ data }: Props) {
  const dates = data.map((d) => d.date)
  const profits = data.map((d) => d.totalProfit)
  const values = data.map((d) => d.totalMarketValue)

  const option = {
    tooltip: { trigger: 'axis' as const },
    legend: { data: ['总市值', '总盈亏'] },
    grid: { left: 60, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category' as const, data: dates },
    yAxis: [
      { type: 'value' as const, name: '市值' },
      { type: 'value' as const, name: '盈亏' },
    ],
    series: [
      {
        name: '总市值', type: 'line', data: values,
        smooth: true, lineStyle: { color: '#1890ff', width: 2 },
        itemStyle: { color: '#1890ff' },
      },
      {
        name: '总盈亏', type: 'bar', data: profits, yAxisIndex: 1,
        itemStyle: { color: (params: any) => params.value >= 0 ? '#f5222d' : '#52c41a' },
      },
    ],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
```

- [ ] **Step 2: 创建 DistributionChart 组件**

```tsx
import ReactEChartsCore from 'echarts-for-react'
import type { DistributionItem } from '../types'

interface Props {
  data: DistributionItem[]
}

export default function DistributionChart({ data }: Props) {
  const option = {
    tooltip: {
      trigger: 'item' as const,
      formatter: (params: any) => `${params.name}: ¥${params.value.toLocaleString()} (${params.percent}%)`,
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      data: data.map((d) => ({ name: d.fundName, value: d.value })),
      label: { formatter: '{b}: {d}%' },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
      },
    }],
  }

  return <ReactEChartsCore option={option} style={{ height: 400 }} />
}
```

- [ ] **Step 3: 创建 Analysis 页面**

```tsx
import { useQuery } from '@tanstack/react-query'
import { Card, Row, Col, Statistic, Spin, Alert } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'
import { fetchAnalysis } from '../api/analysis'
import ProfitChart from '../components/ProfitChart'
import DistributionChart from '../components/DistributionChart'
import { formatMoney, formatPercent } from '../utils/format'

export default function Analysis() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analysis'],
    queryFn: fetchAnalysis,
  })

  if (isLoading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />
  if (isError) return <Alert type="error" message="加载分析数据失败" banner />

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card><Statistic title="持仓总市值" value={data?.totalMarketValue || 0} precision={2} prefix="¥" /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="总成本" value={data?.totalCost || 0} precision={2} prefix="¥" /></Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总盈亏"
              value={data?.totalProfit || 0}
              precision={2}
              prefix={data?.totalProfit && data.totalProfit >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              valueStyle={{ color: data?.totalProfit && data.totalProfit >= 0 ? '#f5222d' : '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总收益率"
              value={data?.totalProfitRate || 0}
              precision={2}
              suffix="%"
              valueStyle={{ color: data?.totalProfitRate && data.totalProfitRate >= 0 ? '#f5222d' : '#52c41a' }}
            />
          </Card>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={14}>
          <Card title="收益趋势">
            {data?.profitTrend ? <ProfitChart data={data.profitTrend} /> : <Alert message="暂无数据" type="info" />}
          </Card>
        </Col>
        <Col span={10}>
          <Card title="持仓分布">
            {data?.distribution ? <DistributionChart data={data.distribution} /> : <Alert message="暂无数据" type="info" />}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
```

---

## 自检清单

- [ ] Spec 覆盖：所有 MVP 功能（基金自选、持仓管理、交易记录、收益分析、基金详情）均在以上任务中实现
- [ ] 占位符检查：无 TBD/TODO 占位符，所有代码完整可运行
- [ ] 类型一致性：前后端的 Fund/Holding/Transaction 模型保持一致，API 路径和参数匹配

## 执行方式

计划已保存。推荐使用 **Subagent-Driven** 方式执行 — 逐任务派发子代理，每个任务独立构建并验证，这样可以在隔离环境中逐步构建整个应用。

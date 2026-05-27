package com.fundtracker.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundMapper;
import com.fundtracker.mapper.HoldingMapper;
import com.fundtracker.mapper.TransactionMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.Holding;
import com.fundtracker.model.entity.Transaction;
import com.fundtracker.service.DataSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final FundMapper fundMapper;
    private final HoldingMapper holdingMapper;
    private final TransactionMapper transactionMapper;
    private final DataSyncService dataSyncService;

    public DataInitializer(FundMapper fundMapper, HoldingMapper holdingMapper,
                           TransactionMapper transactionMapper, DataSyncService dataSyncService) {
        this.fundMapper = fundMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.dataSyncService = dataSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 如果已有数据，跳过初始化
        if (fundMapper.selectCount(null) > 0) {
            log.info("数据库已有数据，跳过初始化");
            // 同步最新净值
            try {
                DataSyncService.SyncResult result = dataSyncService.syncAll();
                log.info("数据同步完成: 全量基金 {} 新入库, 实时净值 {}/{} 成功, 历史净值 {}/{} 成功",
                    result.listInserted, result.navSuccess, result.navSuccess + result.navFail,
                    result.historySuccess, result.historySuccess + result.historyFail);
            } catch (Exception e) {
                log.warn("数据同步失败: {}", e.getMessage());
            }
        } else {
            // 从天天基金同步
            log.info("首次启动，从天天基金同步数据...");
            DataSyncService.SyncResult syncResult;
            try {
                syncResult = dataSyncService.syncAll();
                log.info("同步结果: 全量基金 {} 新入库, 实时净值 {}/{} 成功, 历史净值 {}/{} 成功",
                    syncResult.listInserted, syncResult.navSuccess, syncResult.navSuccess + syncResult.navFail,
                    syncResult.historySuccess, syncResult.historySuccess + syncResult.historyFail);
            } catch (Exception e) {
                log.warn("实时同步失败: {}", e.getMessage());
                syncResult = new DataSyncService.SyncResult();
            }

            // 如果同步失败或没有数据，用内置种子数据兜底
            if (fundMapper.selectCount(null) == 0) {
                log.info("使用内置种子数据兜底");
                seedFundData();
            }

            // 初始化持仓和交易（仅首次）
            if (holdingMapper.selectCount(null) == 0) {
                seedHoldingAndTransaction();
            }
        }

        // 后台批量同步所有净值为0的基金
        long pendingCount = fundMapper.selectCount(
            new LambdaQueryWrapper<Fund>().eq(Fund::getNav, BigDecimal.ZERO)
        );
        if (pendingCount > 0) {
            log.info("发现 {} 只基金净值为0，启动后台批量同步", pendingCount);
            new Thread(() -> {
                try {
                    DataSyncService.SyncResult r = dataSyncService.syncAllNavBatch();
                    log.info("后台批量净值同步完成: 成功 {}, 失败 {}", r.navSuccess, r.navFail);
                } catch (Exception e) {
                    log.warn("后台批量净值同步失败: {}", e.getMessage());
                }
            }, "nav-batch-sync").start();
        } else {
            log.info("所有基金净值已就绪");
        }
    }

    private void seedFundData() {
        Fund[] seeds = {
            createFund("110011", "易方达中小盘混合", "混合型-偏股", "4.8523", "2026-05-22", "0.0123", "2008-06-19", "易方达基金"),
            createFund("005827", "易方达蓝筹精选混合", "混合型-偏股", "1.6789", "2026-05-22", "-0.0087", "2018-09-05", "易方达基金"),
            createFund("008283", "华安科技动力混合", "混合型-偏股", "3.2156", "2026-05-22", "0.0056", "2011-12-20", "华安基金"),
            createFund("110003", "易方达上证50增强", "指数型", "2.1458", "2026-05-22", "0.0032", "2004-03-22", "易方达基金"),
            createFund("000001", "华夏成长混合", "混合型", "1.2345", "2026-05-22", "0.0021", "2001-12-18", "华夏基金"),
            createFund("002001", "华夏回报混合", "混合型", "1.5678", "2026-05-22", "0.0015", "2003-09-05", "华夏基金"),
            createFund("070001", "嘉实成长收益混合", "混合型", "2.3456", "2026-05-22", "-0.0034", "2002-11-05", "嘉实基金"),
            createFund("040001", "华安创新混合", "混合型", "1.1234", "2026-05-22", "0.0045", "2001-09-21", "华安基金"),
            createFund("160105", "南方积极配置混合", "混合型", "1.8765", "2026-05-22", "0.0067", "2004-10-14", "南方基金"),
            createFund("233001", "大摩基础行业混合", "混合型", "0.9876", "2026-05-22", "-0.0012", "2004-03-26", "摩根士丹利基金"),
            createFund("260108", "景顺长城新兴成长", "混合型", "2.5678", "2026-05-22", "0.0089", "2006-06-28", "景顺长城基金"),
            createFund("163402", "兴全趋势投资混合", "混合型", "1.4567", "2026-05-22", "0.0034", "2005-11-03", "兴证全球基金"),
        };
        for (Fund f : seeds) {
            fundMapper.insert(f);
        }
    }

    private Fund createFund(String code, String name, String type, String nav, String navDate,
                            String dayInc, String estDate, String company) {
        Fund f = new Fund();
        f.setCode(code);
        f.setName(name);
        f.setType(type);
        f.setNav(new BigDecimal(nav));
        f.setNavDate(LocalDate.parse(navDate));
        f.setDayIncrease(new BigDecimal(dayInc));
        f.setEstablishDate(LocalDate.parse(estDate));
        f.setCompany(company);
        return f;
    }

    private void seedHoldingAndTransaction() {
        Holding h1 = new Holding();
        h1.setFundCode("110011");
        h1.setFundName("易方达中小盘混合");
        h1.setShares(new BigDecimal("5000.00"));
        h1.setCostNav(new BigDecimal("4.5231"));
        h1.setBuyDate(LocalDate.parse("2025-10-15"));
        holdingMapper.insert(h1);

        Holding h2 = new Holding();
        h2.setFundCode("005827");
        h2.setFundName("易方达蓝筹精选混合");
        h2.setShares(new BigDecimal("8000.00"));
        h2.setCostNav(new BigDecimal("1.5234"));
        h2.setBuyDate(LocalDate.parse("2025-08-20"));
        holdingMapper.insert(h2);

        Holding h3 = new Holding();
        h3.setFundCode("260108");
        h3.setFundName("景顺长城新兴成长");
        h3.setShares(new BigDecimal("3000.00"));
        h3.setCostNav(new BigDecimal("2.3456"));
        h3.setBuyDate(LocalDate.parse("2026-01-10"));
        holdingMapper.insert(h3);

        // 交易记录
        Transaction t1 = new Transaction();
        t1.setFundCode("110011"); t1.setType("BUY"); t1.setAmount(new BigDecimal("22615.50"));
        t1.setNav(new BigDecimal("4.5231")); t1.setShares(new BigDecimal("5000.00"));
        t1.setTransactionDate(LocalDateTime.parse("2025-10-15T09:30:00")); t1.setNote("初次建仓");
        transactionMapper.insert(t1);

        Transaction t2 = new Transaction();
        t2.setFundCode("005827"); t2.setType("BUY"); t2.setAmount(new BigDecimal("12187.20"));
        t2.setNav(new BigDecimal("1.5234")); t2.setShares(new BigDecimal("8000.00"));
        t2.setTransactionDate(LocalDateTime.parse("2025-08-20T10:00:00")); t2.setNote("定投买入");
        transactionMapper.insert(t2);

        Transaction t3 = new Transaction();
        t3.setFundCode("260108"); t3.setType("BUY"); t3.setAmount(new BigDecimal("7036.80"));
        t3.setNav(new BigDecimal("2.3456")); t3.setShares(new BigDecimal("3000.00"));
        t3.setTransactionDate(LocalDateTime.parse("2026-01-10T14:00:00")); t3.setNote("逢低买入");
        transactionMapper.insert(t3);

        Transaction t4 = new Transaction();
        t4.setFundCode("110011"); t4.setType("BUY"); t4.setAmount(new BigDecimal("9650.00"));
        t4.setNav(new BigDecimal("4.8250")); t4.setShares(new BigDecimal("2000.00"));
        t4.setTransactionDate(LocalDateTime.parse("2026-03-05T09:45:00")); t4.setNote("加仓");
        transactionMapper.insert(t4);

        Transaction t5 = new Transaction();
        t5.setFundCode("005827"); t5.setType("SELL"); t5.setAmount(new BigDecimal("5000.00"));
        t5.setNav(new BigDecimal("1.6500")); t5.setShares(new BigDecimal("3030.30"));
        t5.setTransactionDate(LocalDateTime.parse("2026-04-12T11:00:00")); t5.setNote("部分止盈");
        transactionMapper.insert(t5);
    }
}

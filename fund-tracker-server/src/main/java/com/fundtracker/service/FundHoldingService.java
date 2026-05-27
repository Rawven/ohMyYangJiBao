package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.FundHoldingMapper;
import com.fundtracker.model.entity.FundHolding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(rollbackFor = Exception.class)
public class FundHoldingService {
    private static final Logger log = LoggerFactory.getLogger(FundHoldingService.class);
    private static final String FUND_PAGE_URL = "https://fund.eastmoney.com/{code}.html";

    private final FundHoldingMapper fundHoldingMapper;

    public FundHoldingService(FundHoldingMapper fundHoldingMapper) {
        this.fundHoldingMapper = fundHoldingMapper;
    }

    public List<FundHolding> getHoldings(String fundCode) {
        List<FundHolding> cached = fundHoldingMapper.selectList(
            new LambdaQueryWrapper<FundHolding>().eq(FundHolding::getFundCode, fundCode)
        );
        if (!cached.isEmpty()) {
            return cached;
        }
        return scrapeAndSave(fundCode);
    }

    private List<FundHolding> scrapeAndSave(String fundCode) {
        List<FundHolding> result = new ArrayList<>();
        try {
            String url = FUND_PAGE_URL.replace("{code}", fundCode);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "text/html");

            String html;
            try (InputStream is = conn.getInputStream()) {
                html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // 提取报告日期
            LocalDate reportDate = LocalDate.now();
            Matcher dateMatcher = Pattern.compile("持仓截止日期:\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(html);
            if (dateMatcher.find()) {
                reportDate = LocalDate.parse(dateMatcher.group(1), DateTimeFormatter.ISO_LOCAL_DATE);
            }

            // 提取持仓表格：找 <th>股票名称</th> 和 </table> 之间的内容
            int tableStart = html.indexOf("<th class=\"alignLeft\">股票名称</th>");
            if (tableStart < 0) {
                // 尝试另一种格式
                tableStart = html.indexOf(">股票名称</th>");
            }
            if (tableStart < 0) {
                log.warn("未找到 {} 的持仓表格", fundCode);
                return result;
            }
            int tableEnd = html.indexOf("</table>", tableStart);
            if (tableEnd < 0) tableEnd = html.length();
            String tableHtml = html.substring(tableStart, tableEnd + 8);

            // 按 <tr> 分割提取每一行
            String[] rows = tableHtml.split("</tr>");
            for (String row : rows) {
                if (!row.contains("quote.eastmoney")) continue; // 跳过表头

                // 提取股票名称: title="贵州茅台" 或 >贵州茅台</a>
                String stockName = "";
                Matcher nameMatcher = Pattern.compile("title=\"([^\"]*)\"").matcher(row);
                if (nameMatcher.find()) {
                    stockName = nameMatcher.group(1).trim();
                }

                // 提取股票代码: stockcode="stock_600519"
                String stockCode = "";
                Matcher codeMatcher = Pattern.compile("stockcode=\"stock_([^\"]*)\"").matcher(row);
                if (codeMatcher.find()) {
                    stockCode = codeMatcher.group(1).trim();
                }

                // 提取持仓占比: <td class="alignRight bold">9.91%</td>
                String holdRatio = "";
                Matcher ratioMatcher = Pattern.compile("</a>\\s*</td>\\s*<td[^>]*>([^<]*)%?").matcher(row);
                if (ratioMatcher.find()) {
                    holdRatio = ratioMatcher.group(1).trim();
                }

                // 提取涨跌幅: <span class="ui-color-green">-0.33%</span>
                String changeRatio = "";
                Matcher changeMatcher = Pattern.compile("<span[^>]*>([^<]*)%?</span>").matcher(row);
                if (changeMatcher.find()) {
                    changeRatio = changeMatcher.group(1).trim();
                }

                if (stockName.isEmpty()) continue;

                FundHolding h = new FundHolding();
                h.setFundCode(fundCode);
                h.setStockName(stockName);
                h.setStockCode(stockCode);
                h.setHoldRatio(parsePercent(holdRatio));
                h.setChangeRatio(parsePercent(changeRatio));
                h.setReportDate(reportDate);
                result.add(h);
            }

            // 存入数据库，先删旧数据再写新数据
            fundHoldingMapper.delete(new LambdaQueryWrapper<FundHolding>().eq(FundHolding::getFundCode, fundCode));
            for (FundHolding h : result) {
                try {
                    fundHoldingMapper.insert(h);
                } catch (Exception e) {
                    log.warn("保存持仓失败 {}: {}", h.getStockName(), e.getMessage());
                }
            }
            log.info("{} 持仓数据已更新: {} 只股票", fundCode, result.size());
        } catch (Exception e) {
            log.warn("获取 {} 持仓失败: {}", fundCode, e.getMessage());
        }
        return result;
    }

    private BigDecimal parsePercent(String val) {
        if (val == null || val.trim().isEmpty() || val.trim().equals("--")) return null;
        try {
            return new BigDecimal(val.trim().replace("%", "").replace("+", ""));
        } catch (Exception e) {
            return null;
        }
    }
}

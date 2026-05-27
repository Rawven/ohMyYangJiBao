package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundtracker.mapper.HoldingMapper;
import com.fundtracker.model.entity.Fund;
import com.fundtracker.model.entity.Holding;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PhotoParseService {

    private static final Logger log = LoggerFactory.getLogger(PhotoParseService.class);

    private final DeepSeekService deepSeekService;
    private final HoldingMapper holdingMapper;
    private final FundService fundService;
    private final ObjectMapper objectMapper;

    public PhotoParseService(DeepSeekService deepSeekService, HoldingMapper holdingMapper,
                             FundService fundService, ObjectMapper objectMapper) {
        this.deepSeekService = deepSeekService;
        this.holdingMapper = holdingMapper;
        this.fundService = fundService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> parseImage(MultipartFile file) throws Exception {
        String ext = ".tmp";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        File tempFile = File.createTempFile("holding_", ext);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }

        try {
            // 图片预处理 — 转为 PNG（避免部分 JPEG 兼容问题）
            try {
                BufferedImage original = ImageIO.read(tempFile);
                if (original != null) {
                    int w = original.getWidth();
                    int h = original.getHeight();
                    if (w < 600 || h < 400) {
                        original = preprocessImage(original);
                    }
                    tempFile.delete();
                    tempFile = File.createTempFile("holding_", ".png");
                    ImageIO.write(original, "png", tempFile);
                }
            } catch (Exception e) {
                log.warn("ImageIO 读取失败，尝试 sips 转换: {}", e.getMessage());
                File pngFile = File.createTempFile("holding_", ".png");
                ProcessBuilder pb = new ProcessBuilder("sips", "-s", "format", "png",
                        tempFile.getAbsolutePath(), "--out", pngFile.getAbsolutePath());
                Process p = pb.start();
                int exit = p.waitFor();
                if (exit == 0 && pngFile.length() > 0) {
                    tempFile.delete();
                    tempFile = pngFile;
                } else {
                    pngFile.delete();
                }
            }

            // OCR 识别
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("/opt/homebrew/share/tessdata/");
            tesseract.setLanguage("chi_sim+eng");
            tesseract.setPageSegMode(3);
            String ocrText = tesseract.doOCR(tempFile);
            log.info("OCR 识别结果: {}", ocrText);

            if (ocrText.isBlank()) {
                throw new RuntimeException("OCR 未能识别到文字，请确认图片包含清晰的持仓信息");
            }

            // DeepSeek 解析
            String prompt = buildPrompt(ocrText);
            String deepSeekResponse = deepSeekService.callDeepSeekWithPrompt(prompt);
            log.info("DeepSeek 解析结果: {}", deepSeekResponse);

            List<Map<String, Object>> holdings = parseDeepSeekResult(deepSeekResponse);

            // 补全基金代码
            for (Map<String, Object> h : holdings) {
                String code = (String) h.get("fundCode");
                String name = (String) h.get("fundName");
                if ((code == null || code.isEmpty()) && name != null && !name.isEmpty()) {
                    String matchedCode = lookupFundCode(name);
                    if (matchedCode != null) {
                        h.put("fundCode", matchedCode);
                    }
                }
            }

            // 根据 amount + 当前净值反算 shares 和 costNav
            for (Map<String, Object> h : holdings) {
                enrichWithAmount(h);
            }

            // 清理中间字段
            for (Map<String, Object> h : holdings) {
                h.remove("amount");
                h.remove("holdingReturn");
            }

            return holdings;
        } finally {
            tempFile.delete();
        }
    }

    public List<Map<String, Object>> replaceHoldings(List<Map<String, Object>> holdings) {
        holdingMapper.delete(new LambdaQueryWrapper<>());

        for (Map<String, Object> item : holdings) {
            Holding h = new Holding();
            h.setFundCode((String) item.getOrDefault("fundCode", ""));
            h.setFundName((String) item.getOrDefault("fundName", ""));
            Object sharesObj = item.get("shares");
            Object costNavObj = item.get("costNav");
            h.setShares(sharesObj != null ? new BigDecimal(sharesObj.toString()) : BigDecimal.ZERO);
            h.setCostNav(costNavObj != null ? new BigDecimal(costNavObj.toString()) : BigDecimal.ZERO);
            h.setBuyDate(LocalDate.now());
            holdingMapper.insert(h);
        }

        return holdings;
    }

    private String lookupFundCode(String fundName) {
        var page = fundService.listFunds(fundName, null, 1, 5);
        List<Fund> funds = page.getRecords();
        if (funds != null && !funds.isEmpty()) {
            return funds.get(0).getCode();
        }

        String cleaned = fundName
                .replaceAll("灵活配置", "")
                .replaceAll(" ", "");
        if (!cleaned.equals(fundName)) {
            page = fundService.listFunds(cleaned, null, 1, 5);
            funds = page.getRecords();
            if (funds != null && !funds.isEmpty()) {
                return funds.get(0).getCode();
            }
        }

        if (fundName.length() > 4) {
            String prefix = fundName.substring(0, Math.min(fundName.length(), 8));
            page = fundService.listFunds(prefix, null, 1, 5);
            funds = page.getRecords();
            if (funds != null && !funds.isEmpty()) {
                return funds.get(0).getCode();
            }
        }

        return null;
    }

    private BufferedImage preprocessImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage scaled = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, w * 2, h * 2, null);
        g2d.dispose();
        return scaled;
    }

    private String buildPrompt(String ocrText) {
        return """
你是一个基金持仓数据解析器。以下是从一张基金 App 持仓截图中 OCR 识别出的原始文本，请提取每只基金的持仓信息。

原始文本：
%s

要求：
1. 识别每只基金的名称（fundName）、持有市值/金额（amount）、持有收益/浮动盈亏（holdingReturn）
2. 如果原始文本中出现了基金代码（6位数字），提取到 fundCode 字段
3. 同一只基金的名字可能被 OCR 分割成多行，请合并成完整的基金名称
4. amount 是正数金额，holdingReturn 是持有收益（可能为负数）
5. shares（份额）和 costNav（成本净值）如果图片中出现了就填，没有则填 0
6. 最多返回 50 条记录

请严格按以下 JSON 格式返回，不要包含 markdown 代码块标记：
{
  "holdings": [
    {"fundCode": "110011", "fundName": "易方达中小盘混合", "shares": 5000.00, "costNav": 4.5231, "amount": 22573.50, "holdingReturn": 1520.30},
    {"fundCode": "", "fundName": "基金名称", "shares": 0, "costNav": 0, "amount": 0, "holdingReturn": 0}
  ]
}
""".formatted(ocrText);
    }

    private List<Map<String, Object>> parseDeepSeekResult(String json) {
        try {
            String cleaned = json;
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)```(?:json)?\\s*", "").trim();
            }

            Map<String, Object> root = objectMapper.readValue(cleaned,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> holdings = (List<Map<String, Object>>) root.get("holdings");
            if (holdings == null) {
                throw new RuntimeException("解析结果中没有 holdings 字段");
            }

            return holdings;
        } catch (Exception e) {
            log.error("解析 DeepSeek 结果失败: {} | 原文: {}", e.getMessage(), json);
            throw new RuntimeException("AI 解析持仓数据失败，请重试或换一张截图");
        }
    }

    /**
     * 根据 amount 反算 shares 和 costNav
     * amount = shares × currentNav  →  shares = amount / currentNav
     * holdingReturn = shares × (currentNav - costNav)  →  costNav = currentNav - holdingReturn/shares
     */
    private void enrichWithAmount(Map<String, Object> item) {
        Object amountObj = item.get("amount");
        if (amountObj == null) return;
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString().replace(",", ""));
        } catch (Exception e) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        // 如果已有 shares 和 costNav 则跳过
        Object sharesObj = item.get("shares");
        Object costNavObj = item.get("costNav");
        if (sharesObj != null && costNavObj != null) {
            try {
                if (new BigDecimal(sharesObj.toString()).compareTo(BigDecimal.ZERO) > 0
                        && new BigDecimal(costNavObj.toString()).compareTo(BigDecimal.ZERO) > 0) {
                    return;
                }
            } catch (Exception ignored) {}
        }

        BigDecimal currentNav = getCurrentNav(item);
        if (currentNav == null || currentNav.compareTo(BigDecimal.ZERO) <= 0) return;

        // shares = amount / currentNav
        BigDecimal shares = amount.divide(currentNav, 2, RoundingMode.HALF_UP);
        item.put("shares", shares.doubleValue());

        // 如果有持有收益，反推成本净值
        Object returnObj = item.get("holdingReturn");
        if (returnObj != null) {
            try {
                BigDecimal holdingReturn = new BigDecimal(returnObj.toString().replace(",", ""));
                if (holdingReturn.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal costNav = currentNav.subtract(
                            holdingReturn.divide(shares, 8, RoundingMode.HALF_UP));
                    if (costNav.compareTo(BigDecimal.ZERO) > 0) {
                        item.put("costNav", costNav.setScale(4, RoundingMode.HALF_UP).doubleValue());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 兜底：成本净值 = 当前净值
        Object finalCostNav = item.get("costNav");
        if (finalCostNav == null) {
            item.put("costNav", currentNav.setScale(4, RoundingMode.HALF_UP).doubleValue());
        }
    }

    private BigDecimal getCurrentNav(Map<String, Object> item) {
        String code = (String) item.get("fundCode");
        if (code == null || code.isEmpty()) return null;
        Fund fund = fundService.getFundByCode(code);
        if (fund == null || fund.getNav() == null) return null;
        return fund.getNav();
    }
}

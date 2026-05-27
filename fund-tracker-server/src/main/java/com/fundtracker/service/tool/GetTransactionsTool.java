package com.fundtracker.service.tool;

import com.fundtracker.model.entity.Transaction;
import com.fundtracker.service.TransactionService;
import com.fundtracker.service.AiTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetTransactionsTool implements AiTool {

    private final TransactionService transactionService;

    public GetTransactionsTool(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public String getName() {
        return "get_transactions";
    }

    @Override
    public String getDescription() {
        return "获取交易记录";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("page", Map.of("type", "integer", "description", "页码，从1开始"));
        properties.put("size", Map.of("type", "integer", "description", "每页数量"));
        return Map.of("type", "object", "properties", properties, "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> args) {
        try {
            List<Transaction> allTransactions = transactionService.listTransactions(null);
            if (allTransactions == null || allTransactions.isEmpty()) {
                return List.of();
            }

            int page = args.containsKey("page") ? ((Number) args.get("page")).intValue() : 1;
            int size = args.containsKey("size") ? ((Number) args.get("size")).intValue() : allTransactions.size();

            // 手动分页
            int fromIndex = (page - 1) * size;
            if (fromIndex >= allTransactions.size()) {
                return List.of();
            }
            int toIndex = Math.min(fromIndex + size, allTransactions.size());
            List<Transaction> pageList = allTransactions.subList(fromIndex, toIndex);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Transaction t : pageList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", t.getId());
                item.put("fundCode", t.getFundCode());
                item.put("type", t.getType());
                item.put("amount", t.getAmount() != null ? t.getAmount().doubleValue() : null);
                item.put("nav", t.getNav() != null ? t.getNav().doubleValue() : null);
                item.put("shares", t.getShares() != null ? t.getShares().doubleValue() : null);
                item.put("transactionDate", t.getTransactionDate() != null ? t.getTransactionDate().toString() : null);
                item.put("note", t.getNote());
                result.add(item);
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "获取交易记录失败: " + e.getMessage());
        }
    }
}

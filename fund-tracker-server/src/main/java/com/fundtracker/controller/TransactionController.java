package com.fundtracker.controller;

import com.fundtracker.model.entity.Transaction;
import com.fundtracker.model.vo.ApiResponse;
import com.fundtracker.service.TransactionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

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

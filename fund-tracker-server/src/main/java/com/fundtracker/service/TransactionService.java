package com.fundtracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fundtracker.mapper.TransactionMapper;
import com.fundtracker.model.entity.Transaction;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionMapper transactionMapper) {
        this.transactionMapper = transactionMapper;
    }

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

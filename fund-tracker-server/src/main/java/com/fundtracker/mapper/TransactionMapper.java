package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {}

package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.FundHolding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FundHoldingMapper extends BaseMapper<FundHolding> {
}

package com.fundtracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundtracker.model.entity.Holding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HoldingMapper extends BaseMapper<Holding> {}

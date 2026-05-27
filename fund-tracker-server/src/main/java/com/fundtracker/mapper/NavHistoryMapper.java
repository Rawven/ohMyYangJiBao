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

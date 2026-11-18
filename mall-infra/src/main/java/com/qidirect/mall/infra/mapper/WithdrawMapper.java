package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.Withdraw;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface WithdrawMapper extends BaseMapper<Withdraw> {

    /**
     * 对账用：按门店汇总待审核 + 待打款提现金额，应等于钱包冻结金额。
     */
    @Select("SELECT store_id AS storeId, IFNULL(SUM(amount), 0) AS net "
            + "FROM withdraw WHERE status IN (0, 1) GROUP BY store_id")
    List<Map<String, Object>> sumFrozenByStore();
}

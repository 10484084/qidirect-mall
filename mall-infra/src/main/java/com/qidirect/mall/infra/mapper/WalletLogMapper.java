package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.WalletLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface WalletLogMapper extends BaseMapper<WalletLog> {

    /**
     * 对账用：按门店汇总指定账户的流水净额（入账为正、出账为负）。
     */
    @Select("SELECT store_id AS storeId, IFNULL(SUM(direction * amount), 0) AS net "
            + "FROM wallet_log WHERE account = #{account} GROUP BY store_id")
    List<Map<String, Object>> sumNetByAccount(@Param("account") int account);
}

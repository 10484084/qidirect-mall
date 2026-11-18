package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.Wallet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * 钱包行锁。余额变动必须先加锁再读改写，避免并发超扣。
     */
    @Select("SELECT store_id, deposit_balance, commission_balance, frozen_amount, version, updated_at "
            + "FROM wallet WHERE store_id = #{storeId} FOR UPDATE")
    Wallet selectForUpdate(@Param("storeId") Long storeId);
}

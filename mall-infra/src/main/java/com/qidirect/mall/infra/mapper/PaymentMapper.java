package com.qidirect.mall.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qidirect.mall.domain.entity.Payment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT * FROM payment WHERE pay_no = #{payNo} FOR UPDATE")
    Payment selectForUpdateByPayNo(@Param("payNo") String payNo);

    @Select("SELECT * FROM payment WHERE id = #{id} FOR UPDATE")
    Payment selectForUpdate(@Param("id") Long id);
}

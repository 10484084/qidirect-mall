package com.qidirect.mall.domain.vo;

import java.math.BigDecimal;

/**
 * 提成比例配置。
 */
public record CommissionConfigVO(BigDecimal rate1, BigDecimal rate2) {
}

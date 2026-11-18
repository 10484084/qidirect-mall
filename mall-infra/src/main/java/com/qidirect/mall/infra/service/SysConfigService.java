package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.constant.SysConfigKeys;
import com.qidirect.mall.domain.entity.SysConfig;
import com.qidirect.mall.infra.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sys_config 读写。缺省值仅在数据缺失时补齐，不覆盖后台已修改的值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(SysConfigKeys.COMMISSION_RATE_1, new String[]{"0.10", "一级提成比例"});
        DEFAULTS.put(SysConfigKeys.COMMISSION_RATE_2, new String[]{"0.03", "二级提成比例"});
        DEFAULTS.put(SysConfigKeys.WITHDRAW_MIN_AMOUNT, new String[]{"10000", "最低提现金额（分）"});
        DEFAULTS.put(SysConfigKeys.WITHDRAW_DAYS, new String[]{"15,last", "提现开放日，15=每月15日，last=月末"});
        DEFAULTS.put(SysConfigKeys.ORDER_TIMEOUT_MINUTES, new String[]{"10", "订单支付超时（分钟）"});
        DEFAULTS.put(SysConfigKeys.MONTH_SETTLE_DAY, new String[]{"1", "业绩月结日"});
        DEFAULTS.put(SysConfigKeys.SERVICE_WECHAT, new String[]{"", "客服微信"});
        DEFAULTS.put(SysConfigKeys.SERVICE_PHONE, new String[]{"", "客服电话"});
    }

    private final SysConfigMapper sysConfigMapper;

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String defaultValue) {
        SysConfig config = find(key);
        return config == null ? defaultValue : config.getCfgValue();
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : new BigDecimal(value);
    }

    public List<SysConfig> list() {
        return sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getCfgKey));
    }

    @Transactional
    public void set(String key, String value, String remark) {
        SysConfig existing = find(key);
        if (existing == null) {
            SysConfig config = new SysConfig();
            config.setCfgKey(key);
            config.setCfgValue(value);
            config.setRemark(remark == null ? "" : remark);
            sysConfigMapper.insert(config);
            return;
        }
        existing.setCfgValue(value);
        if (remark != null) {
            existing.setRemark(remark);
        }
        sysConfigMapper.updateById(existing);
    }

    /**
     * 应用启动后补齐默认配置；数据库尚未初始化时仅记录告警，不影响启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initDefaults() {
        try {
            DEFAULTS.forEach((key, valueAndRemark) -> {
                if (find(key) == null) {
                    set(key, valueAndRemark[0], valueAndRemark[1]);
                }
            });
        } catch (Exception e) {
            log.warn("sys_config 默认配置初始化跳过：{}", e.toString());
        }
    }

    private SysConfig find(String key) {
        return sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getCfgKey, key));
    }
}

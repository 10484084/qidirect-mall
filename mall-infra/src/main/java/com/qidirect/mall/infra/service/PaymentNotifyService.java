package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.PaymentNotifyLog;
import com.qidirect.mall.infra.mapper.PaymentNotifyLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 支付回调：先落原始报文，再验签、金额比对与幂等分发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotifyService {

    private static final String SUCCESS = "01";
    private static final DateTimeFormatter TERMINAL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentGateway paymentGateway;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final PaymentService paymentService;

    public void handleSaobei(Map<String, String> params, String rawBody) {
        String payNo = StrUtil.blankToDefault(params.get("terminal_trace"), params.get("pay_no"));
        boolean verified = paymentGateway.verifyNotify(params);

        PaymentNotifyLog notifyLog = new PaymentNotifyLog();
        notifyLog.setPayNo(StrUtil.nullToEmpty(payNo));
        notifyLog.setRawBody(StrUtil.nullToEmpty(rawBody));
        notifyLog.setVerified(verified ? 1 : 0);
        notifyLog.setHandled(0);
        paymentNotifyLogMapper.insert(notifyLog);

        if (!verified) {
            log.warn("支付回调验签失败：payNo={}", payNo);
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID);
        }
        if (StrUtil.isBlank(payNo)) {
            log.warn("支付回调缺少支付单号：{}", rawBody);
            return;
        }
        // 4.10 回调仅在支付成功时推送（无 trade_state 字段），以 return_code/result_code=01 判定
        if (!SUCCESS.equals(params.get("return_code")) || !SUCCESS.equals(params.get("result_code"))) {
            log.warn("支付回调非成功状态，忽略：payNo={} body={}", payNo, rawBody);
            markHandled(notifyLog.getId());
            return;
        }
        paymentService.handlePaidNotify(payNo, parseAmount(params.get("total_fee")),
                params.get("out_trade_no"), parsePaidAt(StrUtil.blankToDefault(
                        params.get("end_time"), params.get("pay_time"))));
        markHandled(notifyLog.getId());
    }

    private void markHandled(Long id) {
        paymentNotifyLogMapper.update(null, Wrappers.<PaymentNotifyLog>lambdaUpdate()
                .eq(PaymentNotifyLog::getId, id)
                .set(PaymentNotifyLog::getHandled, 1));
    }

    private long parseAmount(String value) {
        if (StrUtil.isBlank(value)) {
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID.getCode(), "支付回调缺少金额");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID.getCode(), "支付回调金额格式不正确");
        }
    }

    private LocalDateTime parsePaidAt(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(TERMINAL_TIME, DATE_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}

package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Payment;
import com.qidirect.mall.domain.vo.PayPrepayVO;
import com.qidirect.mall.domain.vo.PaymentStatusVO;
import com.qidirect.mall.infra.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付单状态机与支付发起：JSAPI 下单、回调入账、补偿查单、加盟/预存单超时关单。
 * 网关 HTTP 调用一律在数据库事务之外执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAYING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_CLOSED = 4;
    private static final int STATUS_REFUNDED = 5;
    /** 支付渠道：2=聚合码（走扫呗收银台），与 1=JSAPI、3=线下区分 */
    private static final int CHANNEL_QR = 2;
    private static final int SCAN_BATCH_SIZE = 200;

    private final PaymentMapper paymentMapper;
    private final PaymentGateway paymentGateway;
    private final PurchaseService purchaseService;
    private final WechatAuthService wechatAuthService;
    private final TransactionTemplate transactionTemplate;

    @Value("${payment.provider:mock}")
    private String paymentProvider;

    /**
     * 获取支付调起信息：mock 模式返回模拟支付地址，扫呗模式返回 4.11 聚合码收银台链接
     * （微信/支付宝内打开即拉起支付，无需商户自有公众号）。
     */
    public PayPrepayVO prepayInfo(Long storeId, String payNo) {
        Payment payment = requireOwned(storeId, payNo);
        if (Objects.equals(payment.getStatus(), STATUS_SUCCESS)) {
            return new PayPrepayVO(payNo, payment.getStatus(), true, null, null, Map.of(), null);
        }
        assertPayable(payment);
        if (isMock()) {
            return new PayPrepayVO(payNo, payment.getStatus(), false, null, mockPayUrl(payNo), Map.of(), null);
        }
        String cashierUrl = paymentGateway.cashierUrl(payment);
        transactionTemplate.executeWithoutResult(status -> {
            Payment locked = paymentMapper.selectForUpdate(payment.getId());
            if (locked == null || locked.getStatus() == null
                    || locked.getStatus() == STATUS_SUCCESS
                    || locked.getStatus() == STATUS_CLOSED
                    || locked.getStatus() == STATUS_REFUNDED) {
                return;
            }
            // 生成收银台链接后进入支付中，等待回调/查单确认；渠道标记为聚合码(2)
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, locked.getId())
                    .in(Payment::getStatus, STATUS_PENDING, STATUS_PAYING, 3)
                    .set(Payment::getStatus, STATUS_PAYING)
                    .set(Payment::getChannel, CHANNEL_QR));
        });
        return new PayPrepayVO(payNo, STATUS_PAYING, false, null, cashierUrl, Map.of(), qrDataUri(cashierUrl));
    }

    /**
     * 把收银台链接转成二维码图片（data URI）。
     * 微信内直接跳转外部收银台会把 H5 页面顶掉，付完无法回到商城；
     * 页面内长按识别二维码支付，H5 始终保持在前台，轮询即可捕捉到支付结果。
     */
    private String qrDataUri(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        try {
            QrConfig config = new QrConfig(420, 420);
            config.setMargin(1);
            var image = QrCodeUtil.generate(content, config);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("收银台二维码生成失败：payUrl={} error={}", content, e.toString());
            return null;
        }
    }

    /**
     * bridge 页用授权 code 换 openid 后调起 4.3，返回收银台地址或 JSAPI 参数。
     */
    public PayPrepayVO jsapi(Long storeId, String payNo, String code) {
        Payment payment = requireOwned(storeId, payNo);
        if (Objects.equals(payment.getStatus(), STATUS_SUCCESS)) {
            return new PayPrepayVO(payNo, payment.getStatus(), true, null, null, Map.of(), null);
        }
        assertPayable(payment);
        if (isMock()) {
            return new PayPrepayVO(payNo, payment.getStatus(), false, null, mockPayUrl(payNo), Map.of(), null);
        }
        String openId = wechatAuthService.exchangeOpenId(code);
        PaymentGateway.JsapiResult result = paymentGateway.jsapi(payment, openId);
        transactionTemplate.executeWithoutResult(status -> {
            Payment locked = paymentMapper.selectForUpdate(payment.getId());
            if (locked == null || locked.getStatus() == null
                    || locked.getStatus() == STATUS_SUCCESS
                    || locked.getStatus() == STATUS_CLOSED
                    || locked.getStatus() == STATUS_REFUNDED) {
                return;
            }
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, locked.getId())
                    .in(Payment::getStatus, STATUS_PENDING, STATUS_PAYING, 3)
                    .set(Payment::getStatus, STATUS_PAYING)
                    .set(StrUtil.isNotBlank(result.outTradeNo()),
                            Payment::getOutTradeNo, result.outTradeNo()));
        });
        boolean hasJsapiParams = result.payParams() != null && !result.payParams().isEmpty();
        return new PayPrepayVO(payNo, STATUS_PAYING, false, null, result.payUrl(), result.payParams(),
                hasJsapiParams ? null : qrDataUri(result.payUrl()));
    }

    public PaymentStatusVO status(Long storeId, String payNo) {
        Payment payment = requireOwned(storeId, payNo);
        // 非支付中的状态直接返回
        if (!Objects.equals(payment.getStatus(), STATUS_PENDING)
                && !Objects.equals(payment.getStatus(), STATUS_PAYING)) {
            return new PaymentStatusVO(payNo, payment.getStatus(),
                    Objects.equals(payment.getStatus(), STATUS_SUCCESS));
        }
        // 支付中：前端返回页面轮询时主动向扫呗发起 4.5 实时查单，命中已支付立即入账，
        // 让用户从收银台返回后无需等待回调/60s 补偿即可看到支付成功
        if (!isMock()) {
            try {
                PaymentGateway.PaymentQueryResult result = paymentGateway.query(payment);
                if (result.paid()) {
                    completePaid(payment.getId(), result);
                    payment = requireOwned(storeId, payNo);
                }
            } catch (Exception e) {
                log.warn("支付状态实时查单失败，回退读取库内状态：payNo={} error={}", payNo, e.toString());
            }
        }
        payment = paymentMapper.selectById(payment.getId());
        return new PaymentStatusVO(payNo, payment.getStatus(),
                Objects.equals(payment.getStatus(), STATUS_SUCCESS));
    }

    /**
     * 支付成功回调入账。支付行锁 + 状态条件更新保证同一支付单只入账一次。
     */
    @Transactional
    public void handlePaidNotify(String payNo, long totalFee, String outTradeNo, LocalDateTime paidAt) {
        Payment payment = paymentMapper.selectForUpdateByPayNo(payNo);
        if (payment == null) {
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID.getCode(), "支付单不存在");
        }
        if (payment.getAmount() == null || payment.getAmount() != totalFee) {
            log.error("支付回调金额不一致：payNo={} amount={} notify={}",
                    payNo, payment.getAmount(), totalFee);
            throw new BizException(ErrorCode.PAY_NOTIFY_INVALID.getCode(), "支付金额不一致");
        }
        if (Objects.equals(payment.getStatus(), STATUS_SUCCESS)) {
            return;
        }
        boolean closed = Objects.equals(payment.getStatus(), STATUS_CLOSED)
                || Objects.equals(payment.getStatus(), STATUS_REFUNDED);
        int claimed;
        try {
            claimed = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, payment.getId())
                    .ne(Payment::getStatus, STATUS_SUCCESS)
                    .set(Payment::getStatus, STATUS_SUCCESS)
                    .set(Payment::getPaidAt, paidAt == null ? LocalDateTime.now() : paidAt)
                    .set(Payment::getOutTradeNo, StrUtil.nullToEmpty(outTradeNo)));
        } catch (DuplicateKeyException e) {
            log.warn("同一业务单已有成功支付，忽略重复成功：payNo={}", payNo);
            return;
        }
        if (claimed == 0) {
            return;
        }
        if (closed) {
            purchaseService.markAbnormal(payment.getId(), "关单后收到支付成功回调");
        } else {
            purchaseService.dispatchPaid(payment.getId());
        }
    }

    /**
     * 支付补偿：分批扫描全部中间态支付单（不限创建时间），4.5 返回已支付则补记成功。
     * 按 id 游标分页，避免长期卡在支付中的单据因扫描条数上限被永久跳过。
     */
    public int compensate() {
        int completed = 0;
        Long lastId = 0L;
        while (true) {
            List<Payment> batch = paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()
                    .in(Payment::getStatus, STATUS_PENDING, STATUS_PAYING)
                    .gt(Payment::getId, lastId)
                    .orderByAsc(Payment::getId)
                    .last("limit " + SCAN_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (Payment payment : batch) {
                lastId = payment.getId();
                try {
                    PaymentGateway.PaymentQueryResult result = paymentGateway.query(payment);
                    if (!result.paid()) {
                        continue;
                    }
                    completePaid(payment.getId(), result);
                    completed++;
                } catch (Exception e) {
                    log.error("支付补偿查单失败：payNo={} error={}", payment.getPayNo(), e.toString());
                }
            }
            if (batch.size() < SCAN_BATCH_SIZE) {
                break;
            }
        }
        return completed;
    }

    /**
     * 加盟/补差/预存单超时关单，复用 4.8 → 4.5 → 确认未支付后取消的两阶段流程。
     * 按 id 游标分批扫描，避免超过单次扫描条数的过期单据一直排在队尾无法关闭。
     */
    public int closeExpiredPurchases() {
        int closed = 0;
        Long lastId = 0L;
        while (true) {
            List<Payment> batch = paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()
                    .in(Payment::getBizType, "JOIN", "UPGRADE", "RECHARGE")
                    .in(Payment::getStatus, STATUS_PENDING, STATUS_PAYING)
                    .isNotNull(Payment::getExpireAt)
                    .le(Payment::getExpireAt, LocalDateTime.now())
                    .gt(Payment::getId, lastId)
                    .orderByAsc(Payment::getId)
                    .last("limit " + SCAN_BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (Payment payment : batch) {
                lastId = payment.getId();
                try {
                    if (closeExpired(payment)) {
                        closed++;
                    }
                } catch (Exception e) {
                    log.error("超时关单失败：payNo={} error={}", payment.getPayNo(), e.toString());
                }
            }
            if (batch.size() < SCAN_BATCH_SIZE) {
                break;
            }
        }
        return closed;
    }

    private void completePaid(Long paymentId, PaymentGateway.PaymentQueryResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            Payment locked = paymentMapper.selectForUpdate(paymentId);
            if (locked == null || locked.getStatus() == null
                    || locked.getStatus() == STATUS_SUCCESS
                    || locked.getStatus() == STATUS_CLOSED
                    || locked.getStatus() == STATUS_REFUNDED) {
                return;
            }
            int claimed = paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, locked.getId())
                    .ne(Payment::getStatus, STATUS_SUCCESS)
                    .set(Payment::getStatus, STATUS_SUCCESS)
                    .set(Payment::getPaidAt, result.paidAt() == null ? LocalDateTime.now() : result.paidAt())
                    .set(Payment::getOutTradeNo, StrUtil.nullToEmpty(result.outTradeNo())));
            if (claimed == 0) {
                return;
            }
            purchaseService.dispatchPaid(locked.getId());
        });
    }

    private boolean closeExpired(Payment payment) {
        Payment prepared = transactionTemplate.execute(status -> {
            Payment locked = paymentMapper.selectForUpdate(payment.getId());
            if (locked == null || locked.getStatus() == null
                    || locked.getStatus() == STATUS_SUCCESS
                    || locked.getStatus() == STATUS_CLOSED
                    || locked.getStatus() == STATUS_REFUNDED) {
                return null;
            }
            if (locked.getStatus() == STATUS_PENDING) {
                paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                        .eq(Payment::getId, locked.getId())
                        .eq(Payment::getStatus, STATUS_PENDING)
                        .set(Payment::getStatus, STATUS_PAYING));
            }
            locked.setStatus(STATUS_PAYING);
            return locked;
        });
        if (prepared == null) {
            return false;
        }
        try {
            paymentGateway.close(prepared);
        } catch (Exception e) {
            log.error("超时关单调用失败，保留待重试：payNo={} error={}", prepared.getPayNo(), e.toString());
            return false;
        }
        PaymentGateway.PaymentQueryResult result;
        try {
            result = paymentGateway.query(prepared);
        } catch (Exception e) {
            log.error("超时关单查单失败，保留待重试：payNo={} error={}", prepared.getPayNo(), e.toString());
            return false;
        }
        if (result.paid()) {
            completePaid(prepared.getId(), result);
            return false;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Payment locked = paymentMapper.selectForUpdate(prepared.getId());
            if (locked == null || locked.getStatus() == null
                    || locked.getStatus() == STATUS_SUCCESS
                    || locked.getStatus() == STATUS_CLOSED
                    || locked.getStatus() == STATUS_REFUNDED) {
                return;
            }
            if (!purchaseService.cancelUnpaid(locked.getId())) {
                return;
            }
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, locked.getId())
                    .in(Payment::getStatus, STATUS_PENDING, STATUS_PAYING, 3)
                    .set(Payment::getStatus, STATUS_CLOSED));
        });
        return true;
    }

    private Payment requireOwned(Long storeId, String payNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPayNo, payNo)
                .last("limit 1"));
        if (payment == null || !Objects.equals(payment.getStoreId(), storeId)) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "支付单不存在");
        }
        return payment;
    }

    private void assertPayable(Payment payment) {
        Integer status = payment.getStatus();
        if (!Objects.equals(status, STATUS_PENDING) && !Objects.equals(status, STATUS_PAYING)) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付单当前状态不可支付");
        }
        if (payment.getExpireAt() != null && LocalDateTime.now().isAfter(payment.getExpireAt())) {
            throw new BizException(ErrorCode.PAY_FAILED.getCode(), "支付单已过期，请重新下单");
        }
    }

    private boolean isMock() {
        return "mock".equalsIgnoreCase(StrUtil.nullToEmpty(paymentProvider));
    }

    private String mockPayUrl(String payNo) {
        return "/api/notify/mock/pay/" + payNo;
    }
}

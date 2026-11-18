package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.enums.WalletAccount;
import com.qidirect.mall.common.enums.WalletBizType;
import com.qidirect.mall.common.enums.WalletDirection;
import com.qidirect.mall.common.enums.WalletSource;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Wallet;
import com.qidirect.mall.domain.entity.WalletLog;
import com.qidirect.mall.domain.vo.WalletLogVO;
import com.qidirect.mall.domain.vo.WalletVO;
import com.qidirect.mall.infra.mapper.WalletLogMapper;
import com.qidirect.mall.infra.mapper.WalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 钱包服务：预存/佣金两个子账户。所有余额变动先取行锁再读改写，并写 wallet_log。
 * 佣金余额允许为负，后续入账自然抵扣；预存余额不足时拒绝扣减。
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final WalletMapper walletMapper;
    private final WalletLogMapper walletLogMapper;

    public WalletVO getWallet(Long storeId) {
        Wallet wallet = walletMapper.selectById(storeId);
        if (wallet == null) {
            throw new BizException(ErrorCode.WALLET_NOT_FOUND);
        }
        return toVO(wallet);
    }

    public PageResult<WalletLogVO> logs(Long storeId, Integer account, long page, long size) {
        if (account != null && WalletAccount.of(account) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "账户类型不正确");
        }
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<WalletLog> query = Wrappers.<WalletLog>lambdaQuery()
                .eq(WalletLog::getStoreId, storeId)
                .eq(account != null, WalletLog::getAccount, account)
                .orderByDesc(WalletLog::getCreatedAt)
                .orderByDesc(WalletLog::getId);
        IPage<WalletLog> result = walletLogMapper.selectPage(new Page<>(current, pageSize), query);
        List<WalletLogVO> records = result.getRecords().stream().map(this::toLogVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 预存入账（加盟、预存、线下收款、订单退回等）。
     */
    @Transactional
    public long creditDeposit(Long storeId, long amount, WalletBizType bizType, String bizNo,
                              WalletSource source, String proofImages, Long operatorId, String remark) {
        requirePositive(amount);
        Wallet wallet = lockWallet(storeId);
        if (duplicated(storeId, bizType, bizNo)) {
            return wallet.getDepositBalance();
        }
        long balanceAfter = wallet.getDepositBalance() + amount;
        updateWallet(wallet, balanceAfter, wallet.getCommissionBalance(), wallet.getFrozenAmount());
        insertLog(storeId, WalletAccount.DEPOSIT, bizType, WalletDirection.IN, amount, balanceAfter,
                bizNo, source, proofImages, operatorId, remark);
        return balanceAfter;
    }

    /**
     * 预存扣减。预存余额不足直接拒绝，不允许透支。
     */
    @Transactional
    public long deductDeposit(Long storeId, long amount, WalletBizType bizType, String bizNo,
                              WalletSource source, String proofImages, Long operatorId, String remark) {
        requirePositive(amount);
        Wallet wallet = lockWallet(storeId);
        if (duplicated(storeId, bizType, bizNo)) {
            return wallet.getDepositBalance();
        }
        if (wallet.getDepositBalance() < amount) {
            throw new BizException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH);
        }
        long balanceAfter = wallet.getDepositBalance() - amount;
        updateWallet(wallet, balanceAfter, wallet.getCommissionBalance(), wallet.getFrozenAmount());
        insertLog(storeId, WalletAccount.DEPOSIT, bizType, WalletDirection.OUT, amount, balanceAfter,
                bizNo, source, proofImages, operatorId, remark);
        return balanceAfter;
    }

    /**
     * 下单扣预存：仅动预存账户，业务单号幂等。
     */
    @Transactional
    public long deductDepositForOrder(Long storeId, long amount, String orderNo) {
        return deductDeposit(storeId, amount, WalletBizType.ORDER_BALANCE, orderNo,
                WalletSource.ONLINE, null, 0L, "订单预存抵扣");
    }

    /**
     * 取消订单退回预存：业务单号幂等，重复调用不会重复退款。
     */
    @Transactional
    public long refundDepositForOrder(Long storeId, long amount, String orderNo) {
        return creditDeposit(storeId, amount, WalletBizType.ORDER_REFUND, orderNo,
                WalletSource.ONLINE, null, 0L, "订单取消退回预存");
    }

    /**
     * 佣金入账。余额为负时本方法执行后自然向零抵扣。
     */
    @Transactional
    public long creditCommission(Long storeId, long amount, WalletBizType bizType, String bizNo,
                                 WalletSource source, String proofImages, Long operatorId, String remark) {
        requirePositive(amount);
        return applyCommissionDelta(storeId, amount, bizType, bizNo, source, proofImages, operatorId, remark);
    }

    /**
     * 佣金有符号变动（业绩奖冲正可能为负），允许余额为负。
     */
    @Transactional
    public long applyCommissionDelta(Long storeId, long delta, WalletBizType bizType, String bizNo,
                                     WalletSource source, String proofImages, Long operatorId, String remark) {
        if (delta == 0) {
            Wallet wallet = walletMapper.selectById(storeId);
            if (wallet == null) {
                throw new BizException(ErrorCode.WALLET_NOT_FOUND);
            }
            return wallet.getCommissionBalance();
        }
        Wallet wallet = lockWallet(storeId);
        if (duplicated(storeId, bizType, bizNo)) {
            return wallet.getCommissionBalance();
        }
        long balanceAfter = wallet.getCommissionBalance() + delta;
        updateWallet(wallet, wallet.getDepositBalance(), balanceAfter, wallet.getFrozenAmount());
        insertLog(storeId, WalletAccount.COMMISSION, bizType,
                delta > 0 ? WalletDirection.IN : WalletDirection.OUT, Math.abs(delta), balanceAfter,
                bizNo, source, proofImages, operatorId, remark);
        return balanceAfter;
    }

    /**
     * 提现申请冻结佣金：不写余额流水。可用佣金 = 佣金余额 - 已冻结金额。
     */
    @Transactional
    public void freezeCommission(Long storeId, long amount) {
        requirePositive(amount);
        Wallet wallet = lockWallet(storeId);
        long available = wallet.getCommissionBalance() - wallet.getFrozenAmount();
        if (available < amount) {
            throw new BizException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "可提现余额不足");
        }
        updateWallet(wallet, wallet.getDepositBalance(), wallet.getCommissionBalance(),
                wallet.getFrozenAmount() + amount);
    }

    /**
     * 提现驳回或打款失败解冻：不写余额流水。
     */
    @Transactional
    public void unfreezeCommission(Long storeId, long amount) {
        requirePositive(amount);
        Wallet wallet = lockWallet(storeId);
        if (wallet.getFrozenAmount() < amount) {
            throw new BizException(ErrorCode.WALLET_FROZEN_NOT_ENOUGH);
        }
        updateWallet(wallet, wallet.getDepositBalance(), wallet.getCommissionBalance(),
                wallet.getFrozenAmount() - amount);
    }

    /**
     * 提现打款成功：扣减佣金余额与冻结金额，写 WITHDRAW 出账流水，业务单号幂等。
     */
    @Transactional
    public long completeWithdraw(Long storeId, long amount, String withdrawNo,
                                 String proofImages, Long operatorId) {
        requirePositive(amount);
        Wallet wallet = lockWallet(storeId);
        if (duplicated(storeId, WalletBizType.WITHDRAW, withdrawNo)) {
            return wallet.getCommissionBalance();
        }
        if (wallet.getFrozenAmount() < amount) {
            throw new BizException(ErrorCode.WALLET_FROZEN_NOT_ENOUGH);
        }
        long balanceAfter = wallet.getCommissionBalance() - amount;
        updateWallet(wallet, wallet.getDepositBalance(), balanceAfter, wallet.getFrozenAmount() - amount);
        insertLog(storeId, WalletAccount.COMMISSION, WalletBizType.WITHDRAW, WalletDirection.OUT,
                amount, balanceAfter, withdrawNo, WalletSource.OFFLINE, proofImages, operatorId, "提现打款成功");
        return balanceAfter;
    }

    private Wallet lockWallet(Long storeId) {
        return lockWallets(List.of(storeId)).get(0);
    }

    /**
     * 同事务需要锁多个钱包时，必须按 store_id 升序加锁，避免交叉等待死锁。
     */
    private List<Wallet> lockWallets(Collection<Long> storeIds) {
        List<Wallet> wallets = new ArrayList<>(storeIds.size());
        storeIds.stream().distinct().sorted().forEach(storeId -> {
            Wallet wallet = walletMapper.selectForUpdate(storeId);
            if (wallet == null) {
                throw new BizException(ErrorCode.WALLET_NOT_FOUND);
            }
            wallets.add(wallet);
        });
        return wallets;
    }

    private void updateWallet(Wallet wallet, long depositBalance, long commissionBalance, long frozenAmount) {
        LambdaUpdateWrapper<Wallet> update = Wrappers.<Wallet>lambdaUpdate()
                .eq(Wallet::getStoreId, wallet.getStoreId())
                .eq(Wallet::getVersion, wallet.getVersion())
                .set(Wallet::getDepositBalance, depositBalance)
                .set(Wallet::getCommissionBalance, commissionBalance)
                .set(Wallet::getFrozenAmount, frozenAmount)
                .setSql("version = version + 1");
        if (walletMapper.update(null, update) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "钱包更新冲突，请重试");
        }
    }

    private void insertLog(Long storeId, WalletAccount account, WalletBizType bizType, WalletDirection direction,
                           long amount, long balanceAfter, String bizNo, WalletSource source,
                           String proofImages, Long operatorId, String remark) {
        WalletLog log = new WalletLog();
        log.setStoreId(storeId);
        log.setAccount(account.getCode());
        log.setBizType(bizType.name());
        log.setDirection(direction.getCode());
        log.setAmount(amount);
        log.setBalanceAfter(balanceAfter);
        log.setBizNo(StrUtil.nullToEmpty(bizNo));
        log.setSource(source.getCode());
        log.setProofImages(StrUtil.blankToDefault(proofImages, null));
        log.setOperatorId(operatorId == null ? 0L : operatorId);
        log.setRemark(StrUtil.nullToEmpty(remark));
        walletLogMapper.insert(log);
    }

    /**
     * 业务单号幂等：同一门店、同一业务类型、同一单号只记一次账。
     * bizNo 为空（如人工调整）不参与幂等判断。
     */
    private boolean duplicated(Long storeId, WalletBizType bizType, String bizNo) {
        if (StrUtil.isBlank(bizNo)) {
            return false;
        }
        Long count = walletLogMapper.selectCount(Wrappers.<WalletLog>lambdaQuery()
                .eq(WalletLog::getStoreId, storeId)
                .eq(WalletLog::getBizType, bizType.name())
                .eq(WalletLog::getBizNo, bizNo));
        return count != null && count > 0;
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.WALLET_AMOUNT_INVALID);
        }
    }

    private WalletVO toVO(Wallet wallet) {
        return new WalletVO(
                wallet.getStoreId(),
                wallet.getDepositBalance(),
                wallet.getCommissionBalance(),
                wallet.getFrozenAmount(),
                wallet.getCommissionBalance() - wallet.getFrozenAmount());
    }

    private WalletLogVO toLogVO(WalletLog log) {
        return new WalletLogVO(
                log.getId(),
                log.getAccount(),
                log.getBizType(),
                log.getDirection(),
                log.getAmount(),
                log.getBalanceAfter(),
                log.getBizNo(),
                log.getSource(),
                parseImages(log.getProofImages()),
                log.getRemark(),
                log.getCreatedAt());
    }

    private List<String> parseImages(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}

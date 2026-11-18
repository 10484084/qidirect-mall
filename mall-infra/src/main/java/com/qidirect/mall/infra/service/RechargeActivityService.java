package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.domain.entity.RechargeActivity;
import com.qidirect.mall.domain.entity.RechargeActivityTier;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.PurchaseOptionVO;
import com.qidirect.mall.domain.vo.RechargeActivityVO;
import com.qidirect.mall.infra.mapper.RechargeActivityMapper;
import com.qidirect.mall.infra.mapper.RechargeActivityTierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店端预存活动查询。只返回上架且在有效期内的活动，
 * 每个活动下给出适用于当前门店的档位与实付金额（补差档位会提示按差额支付）。
 */
@Service
@RequiredArgsConstructor
public class RechargeActivityService {

    private final RechargeActivityMapper rechargeActivityMapper;
    private final RechargeActivityTierMapper rechargeActivityTierMapper;
    private final StoreLevelService storeLevelService;
    private final StoreGuard storeGuard;
    private final PurchaseService purchaseService;

    public List<RechargeActivityVO> list(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        LocalDateTime now = LocalDateTime.now();
        List<RechargeActivity> activities = rechargeActivityMapper.selectList(
                Wrappers.<RechargeActivity>lambdaQuery()
                        .eq(RechargeActivity::getStatus, 1)
                        .and(w -> w.isNull(RechargeActivity::getStartTime)
                                .or().le(RechargeActivity::getStartTime, now))
                        .and(w -> w.isNull(RechargeActivity::getEndTime)
                                .or().ge(RechargeActivity::getEndTime, now))
                        .orderByDesc(RechargeActivity::getId));
        return activities.stream().map(activity -> toVO(store, activity, now)).toList();
    }

    private RechargeActivityVO toVO(Store store, RechargeActivity activity, LocalDateTime now) {
        List<Long> levelIds = rechargeActivityTierMapper.selectList(
                        Wrappers.<RechargeActivityTier>lambdaQuery()
                                .eq(RechargeActivityTier::getActivityId, activity.getId()))
                .stream().map(RechargeActivityTier::getLevelId).toList();
        List<PurchaseOptionVO> options = levelIds.stream()
                .map(storeLevelService::requireLevel)
                .map((StoreLevel level) -> purchaseService.optionFor(store, level, now))
                .toList();
        return new RechargeActivityVO(activity.getId(), activity.getTitle(), activity.getStartTime(),
                activity.getEndTime(), activity.getLimitPerStore(), options);
    }
}

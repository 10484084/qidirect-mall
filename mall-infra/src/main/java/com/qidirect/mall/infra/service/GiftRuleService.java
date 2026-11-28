package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.GiftRuleSaveRequest;
import com.qidirect.mall.domain.entity.GiftRule;
import com.qidirect.mall.domain.entity.Product;
import com.qidirect.mall.domain.entity.ProductSku;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.GiftRewardVO;
import com.qidirect.mall.domain.vo.GiftRuleVO;
import com.qidirect.mall.infra.mapper.GiftRuleMapper;
import com.qidirect.mall.infra.mapper.ProductMapper;
import com.qidirect.mall.infra.mapper.ProductSkuMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 买赠规则：任一时刻同一商品仅一条 enabled=1；启用新规则时同事务先停用旧规则。
 * 规则按档位独立配置（level_id=0 为通用），赠品按「实付瓶数 / 门槛 × 赠送瓶数」向下取整。
 * 仅当订单使用了预存余额抵扣时才计算赠品，纯在线支付不参与。
 */
@Service
@RequiredArgsConstructor
public class GiftRuleService {

    private final GiftRuleMapper giftRuleMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final StoreLevelMapper storeLevelMapper;

    public List<GiftRuleVO> list(Long productId) {
        return giftRuleMapper.selectList(Wrappers.<GiftRule>lambdaQuery()
                        .eq(productId != null, GiftRule::getProductId, productId)
                        .orderByDesc(GiftRule::getEnabled)
                        .orderByDesc(GiftRule::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public GiftRuleVO create(GiftRuleSaveRequest request) {
        return save(null, request);
    }

    @Transactional
    public GiftRuleVO update(Long id, GiftRuleSaveRequest request) {
        return save(id, request);
    }

    @Transactional
    public GiftRuleVO setEnabled(Long id, boolean enabled) {
        GiftRule rule = requireRule(id);
        if (enabled) {
            assertNotFuture(rule.getStartTime());
            disableOthers(rule.getProductId(), rule.getLevelId(), rule.getId());
            rule.setEnabled(1);
        } else {
            rule.setEnabled(0);
        }
        giftRuleMapper.updateById(rule);
        return toVO(rule);
    }

    @Transactional
    public void delete(Long id) {
        requireRule(id);
        giftRuleMapper.deleteById(id);
    }

    /**
     * 门店端可见的买赠规则：仅最高档位、商品开启 V3 买赠且规则处于启用有效期内。
     */
    public GiftRuleVO visibleRule(Store store, Product product) {
        if (!giftAllowed(store) || !Objects.equals(product.getV3GiftEnabled(), 1)) {
            return null;
        }
        GiftRule rule = activeRule(product.getId(), store.getLevelId(), LocalDateTime.now());
        return rule == null ? null : toVO(rule);
    }

    /**
     * 结算赠品计算。productBottles 为按商品汇总的实付瓶数（已按 factor 折算）。
     */
    public List<GiftRewardVO> calculateGifts(Store store, Map<Long, Integer> productBottles) {
        if (!giftAllowed(store) || productBottles == null || productBottles.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<GiftRewardVO> rewards = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productBottles.entrySet()) {
            int bottles = entry.getValue() == null ? 0 : entry.getValue();
            if (bottles <= 0) {
                continue;
            }
            Product product = productMapper.selectById(entry.getKey());
            if (product == null || !Objects.equals(product.getV3GiftEnabled(), 1)) {
                continue;
            }
            List<GiftRule> rules = activeRules(product.getId(), store.getLevelId(), now);
            if (rules.isEmpty()) {
                continue;
            }
            // 多条阶梯规则：门槛按规格折算成主单位后比较，取「已达成门槛中最高的一档」
            GiftRule best = null;
            int bestThresholdBottles = 0;
            for (GiftRule rule : rules) {
                int thresholdBottles = rule.getThresholdQty() * thresholdFactor(rule);
                if (bottles >= thresholdBottles && thresholdBottles > bestThresholdBottles) {
                    best = rule;
                    bestThresholdBottles = thresholdBottles;
                }
            }
            if (best == null) {
                continue;
            }
            int bestQty = (bottles / bestThresholdBottles) * best.getGiftQty();
            if (bestQty <= 0) {
                continue;
            }
            ProductSku giftSku = productSkuMapper.selectById(best.getGiftSkuId());
            rewards.add(new GiftRewardVO(product.getId(), best.getGiftSkuId(),
                    giftSku == null ? "瓶" : giftSku.getSpecName(), bestQty, 0L));
        }
        return rewards;
    }

    public List<GiftRewardVO> calculateGifts(Store store, Long productId, int bottles) {
        return calculateGifts(store, Map.of(productId, bottles));
    }

    private GiftRuleVO save(Long id, GiftRuleSaveRequest request) {
        Product product = productMapper.selectById(request.productId());
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductSku giftSku = productSkuMapper.selectById(request.giftSkuId());
        if (giftSku == null || !Objects.equals(giftSku.getProductId(), product.getId())) {
            throw new BizException(ErrorCode.GIFT_RULE_INVALID.getCode(), "赠品规格必须属于该商品");
        }
        Long thresholdSkuId = request.thresholdSkuId() == null ? 0L : request.thresholdSkuId();
        if (thresholdSkuId != 0) {
            ProductSku thresholdSku = productSkuMapper.selectById(thresholdSkuId);
            if (thresholdSku == null || !Objects.equals(thresholdSku.getProductId(), product.getId())) {
                throw new BizException(ErrorCode.GIFT_RULE_INVALID.getCode(), "门槛规格必须属于该商品");
            }
        }

        int enabled = request.enabled() != null && request.enabled() == 1 ? 1 : 0;
        if (enabled == 1) {
            assertNotFuture(request.startTime());
        }

        GiftRule rule = id == null ? new GiftRule() : requireRule(id);
        Long levelId = request.levelId() == null ? 0L : request.levelId();
        rule.setProductId(product.getId());
        rule.setLevelId(levelId);
        rule.setThresholdSkuId(thresholdSkuId);
        rule.setGiftSkuId(request.giftSkuId());
        rule.setThresholdQty(request.thresholdQty());
        rule.setGiftQty(request.giftQty());
        rule.setEnabled(enabled);
        rule.setStartTime(request.startTime());
        rule.setEndTime(request.endTime());
        if (id == null) {
            giftRuleMapper.insert(rule);
        } else {
            giftRuleMapper.updateById(rule);
        }
        return toVO(rule);
    }

    /**
     * 一期不支持预排未来规则，启用时禁止未来生效时间。
     */
    private void assertNotFuture(LocalDateTime startTime) {
        if (startTime != null && startTime.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.GIFT_RULE_INVALID.getCode(),
                    "一期不支持预排未来规则，请在生效时手动启用");
        }
    }

    private void disableOthers(Long productId, Long levelId, Long excludeId) {
        giftRuleMapper.update(null, Wrappers.<GiftRule>lambdaUpdate()
                .eq(GiftRule::getProductId, productId)
                .eq(GiftRule::getLevelId, levelId == null ? 0L : levelId)
                .eq(GiftRule::getEnabled, 1)
                .ne(excludeId != null, GiftRule::getId, excludeId)
                .set(GiftRule::getEnabled, 0));
    }

    /**
     * 商品当前生效的买赠规则（可配置多条阶梯规则）。
     */
    private List<GiftRule> activeRules(Long productId, Long levelId, LocalDateTime now) {
        Long lv = levelId == null ? 0L : levelId;
        return giftRuleMapper.selectList(Wrappers.<GiftRule>lambdaQuery()
                        .eq(GiftRule::getProductId, productId)
                        .eq(GiftRule::getEnabled, 1)
                        .and(w -> w.eq(GiftRule::getLevelId, 0L).or().eq(GiftRule::getLevelId, lv))
                        .orderByAsc(GiftRule::getThresholdQty))
                .stream()
                .filter(rule -> rule.getStartTime() == null || !now.isBefore(rule.getStartTime()))
                .filter(rule -> rule.getEndTime() == null || !now.isAfter(rule.getEndTime()))
                .toList();
    }

    private GiftRule activeRule(Long productId, Long levelId, LocalDateTime now) {
        List<GiftRule> rules = activeRules(productId, levelId, now);
        return rules.isEmpty() ? null : rules.get(0);
    }

    /**
     * 所有已开通档位都可参与买赠，具体能拿什么由该档位配置的规则决定。
     */
    private boolean giftAllowed(Store store) {
        return store.getLevelId() != null && store.getLevelId() > 0;
    }

    private GiftRule requireRule(Long id) {
        GiftRule rule = giftRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "买赠规则不存在");
        }
        return rule;
    }

    private GiftRuleVO toVO(GiftRule rule) {
        ProductSku giftSku = productSkuMapper.selectById(rule.getGiftSkuId());
        ProductSku thresholdSku = rule.getThresholdSkuId() == null || rule.getThresholdSkuId() == 0
                ? null : productSkuMapper.selectById(rule.getThresholdSkuId());
        Long lvId = rule.getLevelId() == null ? 0L : rule.getLevelId();
        StoreLevel lv = lvId > 0 ? storeLevelMapper.selectById(lvId) : null;
        return new GiftRuleVO(rule.getId(), rule.getProductId(), lvId,
                lv == null ? "通用" : lv.getName(),
                rule.getThresholdSkuId() == null ? 0L : rule.getThresholdSkuId(),
                thresholdSku == null ? StrUtil.EMPTY : thresholdSku.getSpecName(),
                rule.getGiftSkuId(),
                giftSku == null ? StrUtil.EMPTY : giftSku.getSpecName(), rule.getThresholdQty(),
                rule.getGiftQty(), rule.getEnabled(), rule.getStartTime(), rule.getEndTime());
    }

    /**
     * 门槛规格折算主单位的系数；0 或不存在一律按 1（主单位）处理。
     */
    private int thresholdFactor(GiftRule rule) {
        if (rule.getThresholdSkuId() == null || rule.getThresholdSkuId() == 0) {
            return 1;
        }
        ProductSku sku = productSkuMapper.selectById(rule.getThresholdSkuId());
        return sku == null || sku.getFactor() == null || sku.getFactor() < 1 ? 1 : sku.getFactor();
    }
}

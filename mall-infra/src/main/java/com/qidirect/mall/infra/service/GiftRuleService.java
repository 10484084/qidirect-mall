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
 * 赠品按「实付瓶数 / 门槛 × 赠送瓶数」向下取整，仅最高档位（has_gift=1）且商品开启 V3 买赠时生效。
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
            disableOthers(rule.getProductId(), rule.getId());
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
        GiftRule rule = activeRule(product.getId(), LocalDateTime.now());
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
            List<GiftRule> rules = activeRules(product.getId(), now);
            if (rules.isEmpty()) {
                continue;
            }
            // 多条阶梯规则：取「已达成门槛中最高的一档」执行，符合阶梯买赠的常规语义
            GiftRule best = null;
            for (GiftRule rule : rules) {
                if (bottles >= rule.getThresholdQty()
                        && (best == null || rule.getThresholdQty() > best.getThresholdQty())) {
                    best = rule;
                }
            }
            if (best == null) {
                continue;
            }
            int bestQty = (bottles / best.getThresholdQty()) * best.getGiftQty();
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

        int enabled = request.enabled() != null && request.enabled() == 1 ? 1 : 0;
        if (enabled == 1) {
            assertNotFuture(request.startTime());
        }

        GiftRule rule = id == null ? new GiftRule() : requireRule(id);
        rule.setProductId(product.getId());
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

    private void disableOthers(Long productId, Long excludeId) {
        giftRuleMapper.update(null, Wrappers.<GiftRule>lambdaUpdate()
                .eq(GiftRule::getProductId, productId)
                .eq(GiftRule::getEnabled, 1)
                .ne(excludeId != null, GiftRule::getId, excludeId)
                .set(GiftRule::getEnabled, 0));
    }

    /**
     * 商品当前生效的买赠规则（可配置多条阶梯规则）。
     */
    private List<GiftRule> activeRules(Long productId, LocalDateTime now) {
        return giftRuleMapper.selectList(Wrappers.<GiftRule>lambdaQuery()
                        .eq(GiftRule::getProductId, productId)
                        .eq(GiftRule::getEnabled, 1)
                        .orderByAsc(GiftRule::getThresholdQty))
                .stream()
                .filter(rule -> rule.getStartTime() == null || !now.isBefore(rule.getStartTime()))
                .filter(rule -> rule.getEndTime() == null || !now.isAfter(rule.getEndTime()))
                .toList();
    }

    private GiftRule activeRule(Long productId, LocalDateTime now) {
        List<GiftRule> rules = activeRules(productId, now);
        return rules.isEmpty() ? null : rules.get(0);
    }

    private boolean giftAllowed(Store store) {
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            return false;
        }
        StoreLevel level = storeLevelMapper.selectById(store.getLevelId());
        return level != null && Objects.equals(level.getHasGift(), 1);
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
        return new GiftRuleVO(rule.getId(), rule.getProductId(), rule.getGiftSkuId(),
                giftSku == null ? StrUtil.EMPTY : giftSku.getSpecName(), rule.getThresholdQty(),
                rule.getGiftQty(), rule.getEnabled(), rule.getStartTime(), rule.getEndTime());
    }
}

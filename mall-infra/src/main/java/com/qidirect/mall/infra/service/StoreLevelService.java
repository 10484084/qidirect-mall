package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.LevelSaveRequest;
import com.qidirect.mall.domain.entity.RechargeActivityTier;
import com.qidirect.mall.domain.entity.RechargeOrder;
import com.qidirect.mall.domain.entity.SkuLevelPrice;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.StoreLevelVO;
import com.qidirect.mall.infra.mapper.RechargeActivityTierMapper;
import com.qidirect.mall.infra.mapper.RechargeOrderMapper;
import com.qidirect.mall.infra.mapper.SkuLevelPriceMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 档位管理。长期提成、业绩奖、买赠只能由最高 sort 档位开启。
 * 列表按金额（fee）升序返回，前台只展示启用档位。
 */
@Service
@RequiredArgsConstructor
public class StoreLevelService {

    private static final int DEFAULT_UPGRADE_DAYS = 60;
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_FROZEN = 0;

    private final StoreLevelMapper storeLevelMapper;
    private final StoreMapper storeMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final RechargeActivityTierMapper rechargeActivityTierMapper;
    private final SkuLevelPriceMapper skuLevelPriceMapper;

    public List<StoreLevelVO> list() {
        return storeLevelMapper.selectList(Wrappers.<StoreLevel>lambdaQuery()
                        .orderByAsc(StoreLevel::getFee)
                        .orderByAsc(StoreLevel::getSort))
                .stream().map(this::toVO).toList();
    }

    /**
     * 前台可选档位：仅启用状态，按金额升序。
     */
    public List<StoreLevelVO> listEnabled() {
        return storeLevelMapper.selectList(Wrappers.<StoreLevel>lambdaQuery()
                        .eq(StoreLevel::getStatus, STATUS_ENABLED)
                        .orderByAsc(StoreLevel::getFee)
                        .orderByAsc(StoreLevel::getSort))
                .stream().map(this::toVO).toList();
    }

    public StoreLevel requireLevel(Long levelId) {
        StoreLevel level = storeLevelMapper.selectById(levelId);
        if (level == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "档位不存在");
        }
        return level;
    }

    @Transactional
    public StoreLevelVO create(LevelSaveRequest request) {
        Integer sort = request.sort();
        validateBenefitSwitch(sort, null, request);
        validateNoBenefitBelow(sort, null);
        StoreLevel level = new StoreLevel();
        apply(level, request);
        level.setStatus(STATUS_ENABLED);
        try {
            storeLevelMapper.insert(level);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "档位排序值已存在");
        }
        return toVO(level);
    }

    @Transactional
    public StoreLevelVO update(Long id, LevelSaveRequest request) {
        StoreLevel level = requireLevel(id);
        validateBenefitSwitch(request.sort(), id, request);
        validateNoBenefitBelow(request.sort(), id);
        apply(level, request);
        try {
            storeLevelMapper.updateById(level);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "档位排序值已存在");
        }
        return toVO(level);
    }

    /**
     * 冻结/启用档位。冻结只影响前台能否继续选择，已有门店与订单不受影响。
     */
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_FROZEN)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "档位状态不正确");
        }
        StoreLevel level = requireLevel(id);
        level.setStatus(status);
        storeLevelMapper.updateById(level);
    }

    /**
     * 删除档位：已有门店处于该档位或已产生储值订单时不允许删除，只能冻结。
     * 未使用时级联清理活动档位关联与商品档位价。
     */
    @Transactional
    public void delete(Long id) {
        StoreLevel level = requireLevel(id);
        Long stores = storeMapper.selectCount(Wrappers.<Store>lambdaQuery()
                .eq(Store::getLevelId, id));
        if (stores != null && stores > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "已有 " + stores + " 家门店处于「" + level.getName() + "」，不能删除，请改为冻结");
        }
        Long orders = rechargeOrderMapper.selectCount(Wrappers.<RechargeOrder>lambdaQuery()
                .eq(RechargeOrder::getLevelId, id));
        if (orders != null && orders > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "该档位已产生 " + orders + " 笔储值订单，不能删除，请改为冻结");
        }
        rechargeActivityTierMapper.delete(Wrappers.<RechargeActivityTier>lambdaQuery()
                .eq(RechargeActivityTier::getLevelId, id));
        skuLevelPriceMapper.delete(Wrappers.<SkuLevelPrice>lambdaQuery()
                .eq(SkuLevelPrice::getLevelId, id));
        storeLevelMapper.deleteById(id);
    }

    private void apply(StoreLevel level, LevelSaveRequest request) {
        level.setName(request.name().trim());
        level.setFee(request.fee());
        level.setSort(request.sort());
        level.setUpgradeDays(request.upgradeDays() == null ? DEFAULT_UPGRADE_DAYS : request.upgradeDays());
        level.setLongTerm(flag(request.longTerm()));
        level.setHasBonus(flag(request.hasBonus()));
        level.setHasGift(flag(request.hasGift()));
    }

    /**
     * 非最高档位禁止开启长期提成与团队奖励；买赠按档位在商品档案中独立配置，不受档位高低限制。
     */
    private void validateBenefitSwitch(Integer sort, Long excludeId, LevelSaveRequest request) {
        if (flag(request.longTerm()) == 0 && flag(request.hasBonus()) == 0) {
            return;
        }
        Long higher = storeLevelMapper.selectCount(Wrappers.<StoreLevel>lambdaQuery()
                .gt(StoreLevel::getSort, sort)
                .ne(excludeId != null, StoreLevel::getId, excludeId));
        if (higher != null && higher > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "非最高档位不能开启长期提成或团队奖励");
        }
    }

    /**
     * 新增/上调排序后，原本的最高档位会变成非最高档，必须确保它未开启专属权益。
     */
    private void validateNoBenefitBelow(Integer sort, Long excludeId) {
        Long count = storeLevelMapper.selectCount(Wrappers.<StoreLevel>lambdaQuery()
                .lt(StoreLevel::getSort, sort)
                .ne(excludeId != null, StoreLevel::getId, excludeId)
                .and(wrapper -> wrapper.eq(StoreLevel::getLongTerm, 1)
                        .or().eq(StoreLevel::getHasBonus, 1)));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "存在非最高档位已开启长期提成或团队奖励，请先关闭后再调整排序");
        }
    }

    private int flag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private StoreLevelVO toVO(StoreLevel level) {
        return new StoreLevelVO(level.getId(), level.getName(), level.getFee(), level.getSort(),
                level.getStatus() == null ? STATUS_ENABLED : level.getStatus(),
                level.getUpgradeDays(), level.getLongTerm(), level.getHasBonus(), level.getHasGift());
    }
}

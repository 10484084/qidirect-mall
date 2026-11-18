package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.LevelSaveRequest;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.StoreLevelVO;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 档位管理。长期提成、业绩奖、买赠只能由最高 sort 档位开启。
 */
@Service
@RequiredArgsConstructor
public class StoreLevelService {

    private static final int DEFAULT_UPGRADE_DAYS = 60;

    private final StoreLevelMapper storeLevelMapper;

    public List<StoreLevelVO> list() {
        return storeLevelMapper.selectList(Wrappers.<StoreLevel>lambdaQuery()
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
     * 非最高档位禁止开启专属权益。
     */
    private void validateBenefitSwitch(Integer sort, Long excludeId, LevelSaveRequest request) {
        if (flag(request.longTerm()) == 0 && flag(request.hasBonus()) == 0 && flag(request.hasGift()) == 0) {
            return;
        }
        Long higher = storeLevelMapper.selectCount(Wrappers.<StoreLevel>lambdaQuery()
                .gt(StoreLevel::getSort, sort)
                .ne(excludeId != null, StoreLevel::getId, excludeId));
        if (higher != null && higher > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "非最高档位不能开启长期提成、业绩奖或买赠");
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
                        .or().eq(StoreLevel::getHasBonus, 1)
                        .or().eq(StoreLevel::getHasGift, 1)));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "存在非最高档位已开启专属权益，请先关闭后再调整排序");
        }
    }

    private int flag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private StoreLevelVO toVO(StoreLevel level) {
        return new StoreLevelVO(level.getId(), level.getName(), level.getFee(), level.getSort(),
                level.getUpgradeDays(), level.getLongTerm(), level.getHasBonus(), level.getHasGift());
    }
}

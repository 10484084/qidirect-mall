package com.qidirect.mall.infra.service;

import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.vo.OrderEligibilityVO;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 门店状态守卫：未实名、未开通、冻结均不能下单。
 */
@Service
@RequiredArgsConstructor
public class StoreGuard {

    private final StoreMapper storeMapper;

    public Store requireStore(Long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ErrorCode.STORE_NOT_FOUND);
        }
        return store;
    }

    public void assertNotFrozen(Store store) {
        if (store.getStatus() != null && store.getStatus() == 2) {
            throw new BizException(ErrorCode.STORE_FROZEN);
        }
    }

    /**
     * 开通、升级、预存等购买行为：已实名且未冻结即可，未开通门店可走开通单。
     */
    public Store assertCanPurchase(Long storeId) {
        Store store = requireStore(storeId);
        assertNotFrozen(store);
        if (store.getAuthStatus() == null || store.getAuthStatus() == 0) {
            throw new BizException(ErrorCode.STORE_NOT_VERIFIED);
        }
        return store;
    }

    /**
     * 订货：需已实名且已开通。
     */
    public Store assertCanOrder(Long storeId) {
        Store store = assertCanPurchase(storeId);
        if (store.getLevelId() == null || store.getLevelId() == 0) {
            throw new BizException(ErrorCode.STORE_NOT_JOINED);
        }
        return store;
    }

    public OrderEligibilityVO eligibility(Long storeId) {
        Store store = requireStore(storeId);
        boolean frozen = store.getStatus() != null && store.getStatus() == 2;
        boolean joined = store.getLevelId() != null && store.getLevelId() > 0;
        boolean verified = store.getAuthStatus() != null && store.getAuthStatus() > 0;
        String reason = null;
        if (frozen) {
            reason = ErrorCode.STORE_FROZEN.getMsg();
        } else if (!verified) {
            reason = ErrorCode.STORE_NOT_VERIFIED.getMsg();
        } else if (!joined) {
            reason = ErrorCode.STORE_NOT_JOINED.getMsg();
        }
        return new OrderEligibilityVO(!frozen && verified && joined,
                store.getAuthStatus(), joined, frozen, reason);
    }
}

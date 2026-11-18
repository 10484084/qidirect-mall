package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.store.CartAddRequest;
import com.qidirect.mall.domain.entity.CartItem;
import com.qidirect.mall.domain.entity.Product;
import com.qidirect.mall.domain.entity.ProductSku;
import com.qidirect.mall.domain.entity.SkuLevelPrice;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.vo.CartItemVO;
import com.qidirect.mall.infra.mapper.CartItemMapper;
import com.qidirect.mall.infra.mapper.ProductMapper;
import com.qidirect.mall.infra.mapper.ProductSkuMapper;
import com.qidirect.mall.infra.mapper.SkuLevelPriceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 购物车。价格按当前门店档位实时查询，缺价行标记为不可结算（前台置灰）。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SkuLevelPriceMapper skuLevelPriceMapper;
    private final StoreGuard storeGuard;

    public List<CartItemVO> list(Long storeId) {
        Store store = storeGuard.requireStore(storeId);
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getStoreId, storeId)
                .orderByDesc(CartItem::getId));
        List<CartItemVO> result = new ArrayList<>();
        for (CartItem item : items) {
            CartItemVO vo = toVO(store, item);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    @Transactional
    public CartItemVO add(Long storeId, CartAddRequest request) {
        Store store = storeGuard.requireStore(storeId);
        ProductSku sku = productSkuMapper.selectById(request.skuId());
        if (sku == null) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!Objects.equals(product.getStatus(), 1)) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }

        CartItem item = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getStoreId, storeId)
                .eq(CartItem::getSkuId, sku.getId())
                .last("limit 1"));
        if (item == null) {
            item = new CartItem();
            item.setStoreId(storeId);
            item.setProductId(sku.getProductId());
            item.setSkuId(sku.getId());
            item.setQty(request.qty());
            cartItemMapper.insert(item);
        } else {
            item.setQty(item.getQty() + request.qty());
            cartItemMapper.updateById(item);
        }
        return toVO(store, item);
    }

    @Transactional
    public CartItemVO updateQty(Long storeId, Long itemId, int qty) {
        CartItem item = requireOwned(storeId, itemId);
        if (qty <= 0) {
            cartItemMapper.deleteById(item.getId());
            return null;
        }
        item.setQty(qty);
        cartItemMapper.updateById(item);
        return toVO(storeGuard.requireStore(storeId), item);
    }

    @Transactional
    public void remove(Long storeId, Long itemId) {
        CartItem item = requireOwned(storeId, itemId);
        cartItemMapper.deleteById(item.getId());
    }

    @Transactional
    public void clear(Long storeId) {
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getStoreId, storeId));
    }

    private CartItem requireOwned(Long storeId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getStoreId(), storeId)) {
            throw new BizException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    private CartItemVO toVO(Store store, CartItem item) {
        Product product = productMapper.selectById(item.getProductId());
        ProductSku sku = productSkuMapper.selectById(item.getSkuId());
        if (product == null || sku == null) {
            return null;
        }
        Long unitPrice = null;
        if (store.getLevelId() != null && store.getLevelId() > 0) {
            SkuLevelPrice price = skuLevelPriceMapper.selectOne(Wrappers.<SkuLevelPrice>lambdaQuery()
                    .eq(SkuLevelPrice::getSkuId, sku.getId())
                    .eq(SkuLevelPrice::getLevelId, store.getLevelId())
                    .last("limit 1"));
            unitPrice = price == null ? null : price.getPrice();
        }
        boolean purchasable = unitPrice != null && Objects.equals(product.getStatus(), 1);
        Long amount = unitPrice == null ? null : unitPrice * item.getQty();
        return new CartItemVO(item.getId(), product.getId(), product.getName(), product.getCover(),
                sku.getId(), sku.getSpecName(), sku.getFactor(), item.getQty(), unitPrice, amount,
                purchasable, product.getStatus());
    }
}

package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.ProductSaveRequest;
import com.qidirect.mall.domain.dto.admin.ProductSkuSaveRequest;
import com.qidirect.mall.domain.dto.admin.SkuPriceSaveRequest;
import com.qidirect.mall.domain.entity.CartItem;
import com.qidirect.mall.domain.entity.GiftRule;
import com.qidirect.mall.domain.entity.Product;
import com.qidirect.mall.domain.entity.ProductCategory;
import com.qidirect.mall.domain.entity.ProductSku;
import com.qidirect.mall.domain.entity.SkuLevelPrice;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.vo.AdminProductVO;
import com.qidirect.mall.domain.vo.AdminSkuVO;
import com.qidirect.mall.domain.vo.GiftRewardVO;
import com.qidirect.mall.domain.vo.SkuPriceVO;
import com.qidirect.mall.domain.vo.StoreProductVO;
import com.qidirect.mall.domain.vo.StoreSkuVO;
import com.qidirect.mall.infra.mapper.CartItemMapper;
import com.qidirect.mall.infra.mapper.GiftRuleMapper;
import com.qidirect.mall.infra.mapper.ProductCategoryMapper;
import com.qidirect.mall.infra.mapper.ProductMapper;
import com.qidirect.mall.infra.mapper.ProductSkuMapper;
import com.qidirect.mall.infra.mapper.SkuLevelPriceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 商品管理：后台维护分类/商品/规格/三档价；门店端按当前档位返回价格，缺价档位不可购买。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SkuLevelPriceMapper skuLevelPriceMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final CartItemMapper cartItemMapper;
    private final GiftRuleMapper giftRuleMapper;
    private final GiftRuleService giftRuleService;
    private final StoreGuard storeGuard;

    // ---------------- 后台 ----------------

    public PageResult<AdminProductVO> adminPage(String keyword, Long categoryId, Integer status,
                                                long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<Product> query = Wrappers.<Product>lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), Product::getName, keyword)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getId);
        IPage<Product> result = productMapper.selectPage(new Page<>(current, pageSize), query);
        List<AdminProductVO> records = result.getRecords().stream().map(this::toAdminVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public AdminProductVO adminDetail(Long id) {
        return toAdminVO(requireProduct(id));
    }

    @Transactional
    public AdminProductVO create(ProductSaveRequest request) {
        validateCategory(request.categoryId());
        Product product = new Product();
        apply(product, request);
        productMapper.insert(product);
        for (ProductSkuSaveRequest skuRequest : request.skus()) {
            insertSku(product.getId(), skuRequest);
        }
        return toAdminVO(product);
    }

    @Transactional
    public AdminProductVO update(Long id, ProductSaveRequest request) {
        Product product = requireProduct(id);
        validateCategory(request.categoryId());
        apply(product, request);
        productMapper.updateById(product);
        syncSkus(product.getId(), request.skus());
        return toAdminVO(product);
    }

    /**
     * 下架并逻辑删除商品；同时停用买赠规则、清理购物车，历史订单不受影响。
     */
    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        giftRuleMapper.update(null, Wrappers.<GiftRule>lambdaUpdate()
                .eq(GiftRule::getProductId, id)
                .set(GiftRule::getEnabled, 0));
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getProductId, id));
        productMapper.deleteById(id);
    }

    // ---------------- 门店端 ----------------

    public List<StoreProductVO> storeList(Long storeId, Long categoryId, String keyword) {
        Store store = storeGuard.requireStore(storeId);
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .like(StrUtil.isNotBlank(keyword), Product::getName, keyword)
                .orderByDesc(Product::getRecommend)
                .orderByAsc(Product::getSort)
                .orderByDesc(Product::getId));
        return products.stream().map(product -> toStoreVO(store, product)).toList();
    }

    public StoreProductVO storeDetail(Long storeId, Long productId) {
        Store store = storeGuard.requireStore(storeId);
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!Objects.equals(product.getStatus(), 1)) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF);
        }
        return toStoreVO(store, product);
    }

    /**
     * 按规格与数量预估赠品，供前台结算前展示。实际赠品以下单结算结果为准。
     */
    public List<GiftRewardVO> giftPreview(Long storeId, Long productId, Long skuId, int qty) {
        Store store = storeGuard.requireStore(storeId);
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !Objects.equals(sku.getProductId(), productId)) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return giftRuleService.calculateGifts(store, productId, sku.getFactor() * Math.max(qty, 1));
    }

    // ---------------- 内部 ----------------

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private void validateCategory(Long categoryId) {
        ProductCategory category = productCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品分类不存在");
        }
    }

    private void apply(Product product, ProductSaveRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        product.setUnit(StrUtil.nullToEmpty(StrUtil.trim(request.unit())));
        product.setCover(StrUtil.nullToEmpty(request.cover()));
        product.setImages(JSONUtil.toJsonStr(request.images() == null ? List.of() : request.images()));
        product.setDescription(StrUtil.nullToEmpty(request.description()));
        product.setV3GiftEnabled(flag(request.v3GiftEnabled()));
        product.setRecommend(flag(request.recommend()));
        product.setStatus(request.status() != null && request.status() == 0 ? 0 : 1);
        product.setSort(request.sort() == null ? 0 : request.sort());
    }

    private void insertSku(Long productId, ProductSkuSaveRequest request) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecName(request.specName().trim());
        sku.setFactor(request.factor());
        sku.setSort(request.sort() == null ? 0 : request.sort());
        productSkuMapper.insert(sku);
        replacePrices(sku.getId(), request.prices());
    }

    /**
     * 按 id 同步规格：有 id 更新、无 id 新增、未提交的旧规格删除（被买赠规则引用时拒绝）。
     */
    private void syncSkus(Long productId, List<ProductSkuSaveRequest> requests) {
        List<ProductSku> existing = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId));
        Map<Long, ProductSku> existingById = new HashMap<>();
        existing.forEach(sku -> existingById.put(sku.getId(), sku));

        Set<Long> keptIds = new HashSet<>();
        for (ProductSkuSaveRequest request : requests) {
            if (request.id() == null) {
                insertSku(productId, request);
                continue;
            }
            ProductSku sku = existingById.get(request.id());
            if (sku == null) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "规格不存在或不属于该商品");
            }
            sku.setSpecName(request.specName().trim());
            sku.setFactor(request.factor());
            sku.setSort(request.sort() == null ? 0 : request.sort());
            productSkuMapper.updateById(sku);
            replacePrices(sku.getId(), request.prices());
            keptIds.add(sku.getId());
        }

        for (ProductSku sku : existing) {
            if (keptIds.contains(sku.getId())) {
                continue;
            }
            Long referenced = giftRuleMapper.selectCount(Wrappers.<GiftRule>lambdaQuery()
                    .eq(GiftRule::getGiftSkuId, sku.getId()));
            if (referenced != null && referenced > 0) {
                throw new BizException(ErrorCode.PRODUCT_IN_USE.getCode(), "规格已被买赠规则引用，无法删除");
            }
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getSkuId, sku.getId()));
            skuLevelPriceMapper.delete(Wrappers.<SkuLevelPrice>lambdaQuery()
                    .eq(SkuLevelPrice::getSkuId, sku.getId()));
            productSkuMapper.deleteById(sku.getId());
        }
    }

    private void replacePrices(Long skuId, List<SkuPriceSaveRequest> prices) {
        skuLevelPriceMapper.delete(Wrappers.<SkuLevelPrice>lambdaQuery()
                .eq(SkuLevelPrice::getSkuId, skuId));
        if (prices == null || prices.isEmpty()) {
            return;
        }
        Set<Long> levelIds = new HashSet<>();
        for (SkuPriceSaveRequest request : prices) {
            if (!levelIds.add(request.levelId())) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "同一规格档位价重复");
            }
            SkuLevelPrice price = new SkuLevelPrice();
            price.setSkuId(skuId);
            price.setLevelId(request.levelId());
            price.setPrice(request.price());
            skuLevelPriceMapper.insert(price);
        }
    }

    private AdminProductVO toAdminVO(Product product) {
        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, product.getId())
                .orderByAsc(ProductSku::getSort)
                .orderByAsc(ProductSku::getId));
        Map<Long, List<SkuPriceVO>> pricesBySku = new HashMap<>();
        if (!skus.isEmpty()) {
            List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();
            skuLevelPriceMapper.selectList(Wrappers.<SkuLevelPrice>lambdaQuery()
                            .in(SkuLevelPrice::getSkuId, skuIds))
                    .forEach(price -> pricesBySku.computeIfAbsent(price.getSkuId(), k -> new ArrayList<>())
                            .add(new SkuPriceVO(price.getLevelId(), price.getPrice())));
        }
        List<AdminSkuVO> skuVOs = skus.stream()
                .map(sku -> {
                    List<SkuPriceVO> prices = pricesBySku.getOrDefault(sku.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(SkuPriceVO::levelId))
                            .toList();
                    return new AdminSkuVO(sku.getId(), sku.getSpecName(), sku.getFactor(),
                            sku.getSort(), prices);
                })
                .toList();
        return new AdminProductVO(product.getId(), product.getCategoryId(), product.getName(),
                product.getUnit(), product.getCover(), parseImages(product.getImages()), product.getDescription(),
                product.getV3GiftEnabled(), product.getRecommend(), product.getStatus(),
                product.getSort(), skuVOs);
    }

    private StoreProductVO toStoreVO(Store store, Product product) {
        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, product.getId())
                .orderByAsc(ProductSku::getSort)
                .orderByAsc(ProductSku::getId));
        Map<Long, Long> priceBySku = new LinkedHashMap<>();
        if (!skus.isEmpty() && store.getLevelId() != null && store.getLevelId() > 0) {
            skuLevelPriceMapper.selectList(Wrappers.<SkuLevelPrice>lambdaQuery()
                            .eq(SkuLevelPrice::getLevelId, store.getLevelId())
                            .in(SkuLevelPrice::getSkuId, skus.stream().map(ProductSku::getId).toList()))
                    .forEach(price -> priceBySku.put(price.getSkuId(), price.getPrice()));
        }

        Long minPrice = null;
        List<StoreSkuVO> skuVOs = new ArrayList<>();
        for (ProductSku sku : skus) {
            Long price = priceBySku.get(sku.getId());
            boolean purchasable = price != null;
            if (price != null && (minPrice == null || price < minPrice)) {
                minPrice = price;
            }
            skuVOs.add(new StoreSkuVO(sku.getId(), sku.getSpecName(), sku.getFactor(),
                    sku.getSort(), price, purchasable));
        }
        boolean purchasable = skuVOs.stream().anyMatch(StoreSkuVO::purchasable);
        return new StoreProductVO(product.getId(), product.getCategoryId(), product.getName(),
                product.getCover(), parseImages(product.getImages()), product.getDescription(),
                product.getV3GiftEnabled(), product.getRecommend(), product.getStatus(),
                product.getSort(), store.getLevelId(), minPrice, purchasable, skuVOs,
                giftRuleService.visibleRule(store, product));
    }

    private List<String> parseImages(String images) {
        if (StrUtil.isBlank(images)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(images, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int flag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    /**
     * 按传入顺序重排商品，sort 从 1 开始。
     */
    public void sort(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            if (id == null) {
                continue;
            }
            Product patch = new Product();
            patch.setId(id);
            patch.setSort(i + 1);
            productMapper.updateById(patch);
        }
    }

    /**
     * 上下架。
     */
    public void setStatus(Long id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Product patch = new Product();
        patch.setId(id);
        patch.setStatus(status != null && status == 1 ? 1 : 0);
        productMapper.updateById(patch);
    }
}

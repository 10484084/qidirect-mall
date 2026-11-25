package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.ProductCategorySaveRequest;
import com.qidirect.mall.domain.entity.Product;
import com.qidirect.mall.domain.entity.ProductCategory;
import com.qidirect.mall.domain.vo.ProductCategoryVO;
import com.qidirect.mall.infra.mapper.ProductCategoryMapper;
import com.qidirect.mall.infra.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品分类管理。后台可见全部分类，门店端只返回启用分类。
 */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryMapper productCategoryMapper;
    private final ProductMapper productMapper;

    public List<ProductCategoryVO> list(boolean onlyEnabled) {
        return productCategoryMapper.selectList(Wrappers.<ProductCategory>lambdaQuery()
                        .eq(onlyEnabled, ProductCategory::getStatus, 1)
                        .orderByAsc(ProductCategory::getSort)
                        .orderByAsc(ProductCategory::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public ProductCategoryVO create(ProductCategorySaveRequest request) {
        ProductCategory category = new ProductCategory();
        apply(category, request);
        productCategoryMapper.insert(category);
        return toVO(category);
    }

    @Transactional
    public ProductCategoryVO update(Long id, ProductCategorySaveRequest request) {
        ProductCategory category = requireCategory(id);
        apply(category, request);
        productCategoryMapper.updateById(category);
        return toVO(category);
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PRODUCT_IN_USE.getCode(), "分类下存在商品，无法删除");
        }
        productCategoryMapper.deleteById(id);
    }

    private ProductCategory requireCategory(Long id) {
        ProductCategory category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "商品分类不存在");
        }
        return category;
    }

    private void apply(ProductCategory category, ProductCategorySaveRequest request) {
        category.setName(request.name().trim());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(request.status() != null && request.status() == 0 ? 0 : 1);
    }

    private ProductCategoryVO toVO(ProductCategory category) {
        return new ProductCategoryVO(category.getId(), StrUtil.nullToEmpty(category.getName()),
                category.getSort(), category.getStatus());
    }

    /**
     * 按传入顺序重排分类，sort 从 1 开始。
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
            ProductCategory patch = new ProductCategory();
            patch.setId(id);
            patch.setSort(i + 1);
            productCategoryMapper.updateById(patch);
        }
    }
}

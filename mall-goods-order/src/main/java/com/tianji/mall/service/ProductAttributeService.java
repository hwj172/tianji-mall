package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.mapper.ProductAttributeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeService extends ServiceImpl<ProductAttributeMapper, ProductAttribute> {

    private final ProductAttributeMapper attributeMapper;

    public List<ProductAttribute> listByProductId(Long productId) {
        return list(new LambdaQueryWrapper<ProductAttribute>()
                .eq(ProductAttribute::getProductId, productId)
                .orderByAsc(ProductAttribute::getSort));
    }

    @Transactional
    public ProductAttribute create(Long productId, String name, String value, int sort) {
        ProductAttribute attr = new ProductAttribute();
        attr.setProductId(productId);
        attr.setName(name);
        attr.setValue(value);
        attr.setSort(sort);
        save(attr);
        return attr;
    }

    @Transactional
    public void update(Long productId, Long attrId, String name, String value, Integer sort) {
        ProductAttribute attr = getById(attrId);
        if (attr == null || !attr.getProductId().equals(productId)) {
            throw new BizException(BizErrorCode.ATTRIBUTE_NOT_FOUND);
        }
        if (name != null) attr.setName(name);
        if (value != null) attr.setValue(value);
        if (sort != null) attr.setSort(sort);
        updateById(attr);
    }

    @Transactional
    public void delete(Long productId, Long attrId) {
        ProductAttribute attr = getById(attrId);
        if (attr == null || !attr.getProductId().equals(productId)) {
            throw new BizException(BizErrorCode.ATTRIBUTE_NOT_FOUND);
        }
        removeById(attrId);
    }
}

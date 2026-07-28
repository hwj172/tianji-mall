package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService extends ServiceImpl<AddressMapper, Address> {

    public List<Address> getAddressList(Long userId) {
        return list(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime));
    }

    @Transactional
    public void addAddress(Long userId, AddressRequest req) {
        if (req.getIsDefault() == 1) {
            clearDefault(userId);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(req.getReceiverName());
        address.setPhone(req.getPhone());
        address.setProvince(req.getProvince());
        address.setCity(req.getCity());
        address.setDistrict(req.getDistrict());
        address.setDetail(req.getDetail());
        address.setIsDefault(req.getIsDefault());
        save(address);
        log.info("地址新增: userId={}, receiverName={}", userId, req.getReceiverName());
    }

    @Transactional
    public void updateAddress(Long userId, Long addressId, AddressRequest req) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ADDRESS_NOT_FOUND);
        }
        if (req.getIsDefault() == 1) {
            clearDefault(userId);
        }
        address.setReceiverName(req.getReceiverName());
        address.setPhone(req.getPhone());
        address.setProvince(req.getProvince());
        address.setCity(req.getCity());
        address.setDistrict(req.getDistrict());
        address.setDetail(req.getDetail());
        address.setIsDefault(req.getIsDefault());
        updateById(address);
        log.info("地址更新: userId={}, addressId={}", userId, addressId);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ADDRESS_NOT_FOUND);
        }
        removeById(addressId);
        // 如果删除的是默认地址，将另一个设为默认
        if (address.getIsDefault() == 1) {
            Address another = getOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getCreateTime));
            if (another != null) {
                another.setIsDefault(1);
                updateById(another);
            }
        }
    }

    private void clearDefault(Long userId) {
        update(new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .set(Address::getIsDefault, 0));
    }
}

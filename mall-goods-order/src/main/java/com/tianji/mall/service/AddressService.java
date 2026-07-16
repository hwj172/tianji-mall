package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService extends ServiceImpl<AddressMapper, Address> {

    public List<Address> getAddressList(Long userId) {
        return list(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime));
    }

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
    }

    public void updateAddress(Long userId, Long addressId, AddressRequest req) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException("地址不存在");
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
    }

    public void deleteAddress(Long userId, Long addressId) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException("地址不存在");
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

package com.tianji.mall.service;

import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.mapper.AddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressMapper addressMapper;

    private AddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new AddressService();
        ReflectionTestUtils.setField(addressService, "baseMapper", addressMapper);
    }

    @Test
    void shouldThrowWhenUpdateForeignAddress() {
        Address existing = buildAddress(1L, 200L, "张三");
        when(addressMapper.selectById(1L)).thenReturn(existing);

        AddressRequest req = buildAddressRequest("张三改", "13800001111");

        assertThatThrownBy(() -> addressService.updateAddress(100L, 1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("地址不存在");
    }

    @Test
    void shouldThrowWhenUpdateNonExistentAddress() {
        when(addressMapper.selectById(999L)).thenReturn(null);

        AddressRequest req = buildAddressRequest("张三", "13800001111");

        assertThatThrownBy(() -> addressService.updateAddress(100L, 999L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("地址不存在");
    }

    @Test
    void shouldThrowWhenDeleteForeignAddress() {
        Address existing = buildAddress(1L, 200L, "张三");
        when(addressMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> addressService.deleteAddress(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("地址不存在");
    }

    @Test
    void shouldThrowWhenDeleteNonExistentAddress() {
        when(addressMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> addressService.deleteAddress(100L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessage("地址不存在");
    }

    private Address buildAddress(Long id, Long userId, String name) {
        Address addr = new Address();
        addr.setId(id);
        addr.setUserId(userId);
        addr.setReceiverName(name);
        addr.setPhone("13800000000");
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetail("科技园");
        addr.setIsDefault(0);
        return addr;
    }

    private AddressRequest buildAddressRequest(String name, String phone) {
        AddressRequest req = new AddressRequest();
        req.setReceiverName(name);
        req.setPhone(phone);
        req.setProvince("广东省");
        req.setCity("深圳市");
        req.setDistrict("南山区");
        req.setDetail("科技园");
        req.setIsDefault(0);
        return req;
    }
}

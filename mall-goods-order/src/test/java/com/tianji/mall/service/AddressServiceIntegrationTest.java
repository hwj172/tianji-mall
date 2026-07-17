package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.mapper.AddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AddressServiceIntegrationTest {

    @Autowired
    private AddressService addressService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @Autowired
    private AddressMapper addressMapper;

    @BeforeEach
    void setUp() {
        addressMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== addAddress ====================

    @Test
    void shouldClearDefaultAndSetNew() {
        // 先加一个默认地址
        AddressRequest req1 = buildAddressRequest("张三", "13800000001", 1);
        addressService.addAddress(1L, req1);

        // 再加一个新默认地址
        AddressRequest req2 = buildAddressRequest("李四", "13800000002", 1);
        addressService.addAddress(1L, req2);

        // 旧默认被清除，新的是唯一默认
        List<Address> list = addressService.getAddressList(1L);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getIsDefault()).isEqualTo(1);
        assertThat(list.get(0).getReceiverName()).isEqualTo("李四");
        assertThat(list.get(1).getIsDefault()).isEqualTo(0);
    }

    // ==================== getAddressList ====================

    @Test
    void shouldGetAddressList() {
        AddressRequest req1 = buildAddressRequest("张三", "13800000001", 0);
        addressService.addAddress(1L, req1);
        AddressRequest req2 = buildAddressRequest("李四", "13800000002", 1);
        addressService.addAddress(1L, req2);

        List<Address> list = addressService.getAddressList(1L);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getIsDefault()).isEqualTo(1); // 默认排第一
    }

    // ==================== deleteAddress ====================

    @Test
    void shouldDeleteAddress() {
        AddressRequest req = buildAddressRequest("张三", "13800000001", 0);
        addressService.addAddress(1L, req);
        Long addrId = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, 1L)).get(0).getId();

        addressService.deleteAddress(1L, addrId);

        List<Address> list = addressService.getAddressList(1L);
        assertThat(list).isEmpty();
    }

    @Test
    void shouldThrowWhenDeleteForeignAddress() {
        AddressRequest req = buildAddressRequest("张三", "13800000001", 0);
        addressService.addAddress(1L, req);
        Long addrId = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, 1L)).get(0).getId();

        assertThatThrownBy(() -> addressService.deleteAddress(999L, addrId))
                .isInstanceOf(BizException.class)
                .hasMessage("地址不存在");
    }

    // ==================== updateAddress ====================

    @Test
    void shouldUpdateAddress() {
        AddressRequest req = buildAddressRequest("张三", "13800000001", 0);
        addressService.addAddress(1L, req);
        Long addrId = addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, 1L)).get(0).getId();

        AddressRequest update = buildAddressRequest("张三改", "13900000003", 1);
        addressService.updateAddress(1L, addrId, update);

        Address updated = addressMapper.selectById(addrId);
        assertThat(updated.getReceiverName()).isEqualTo("张三改");
        assertThat(updated.getPhone()).isEqualTo("13900000003");
        assertThat(updated.getIsDefault()).isEqualTo(1);
    }

    // ==================== helpers ====================

    private AddressRequest buildAddressRequest(String name, String phone, int isDefault) {
        AddressRequest req = new AddressRequest();
        req.setReceiverName(name);
        req.setPhone(phone);
        req.setProvince("广东省");
        req.setCity("深圳市");
        req.setDistrict("南山区");
        req.setDetail("科技园路1号");
        req.setIsDefault(isDefault);
        return req;
    }
}

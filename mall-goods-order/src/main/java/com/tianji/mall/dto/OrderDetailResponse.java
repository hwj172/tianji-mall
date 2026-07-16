package com.tianji.mall.dto;

import com.tianji.mall.entity.Address;
import com.tianji.mall.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OrderDetailResponse {

    private Order order;
    private List<OrderItemResponse> items;
    private Address address;
}

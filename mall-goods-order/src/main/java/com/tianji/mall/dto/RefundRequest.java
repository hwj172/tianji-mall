package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RefundRequest {

    @NotBlank(message = "退款原因不能为空")
    private String reason;

    private String refundType = "REFUND_ONLY"; // REFUND_ONLY / RETURN_REFUND

    @NotEmpty(message = "退款商品不能为空")
    private List<RefundItemRequest> items;

    @Data
    public static class RefundItemRequest {
        private Long orderItemId;
        private Long productId;
        private Long skuId;
        private Integer quantity;
    }
}

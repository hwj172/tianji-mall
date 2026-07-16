package com.tianji.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PayResponse {

    /** 支付页面 HTML body（支付宝 pageExecute 返回的表单） */
    private String payForm;
    /** 内部支付流水号 */
    private String paymentNo;
}

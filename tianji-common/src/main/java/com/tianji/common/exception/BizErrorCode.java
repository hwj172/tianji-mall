package com.tianji.common.exception;

import lombok.Getter;

/**
 * 统一业务错误码枚举
 * <p>
 * 编码规则：领域前缀 + 3 位序号
 * <ul>
 *   <li>1xxxx — 通用</li>
 *   <li>10xxx — 用户</li>
 *   <li>20xxx — 商品 / SKU</li>
 *   <li>30xxx — 订单 / 购物车</li>
 *   <li>40xxx — 支付</li>
 *   <li>50xxx — 店铺</li>
 *   <li>60xxx — 优惠券</li>
 *   <li>70xxx — 拼团</li>
 *   <li>80xxx — 秒杀</li>
 *   <li>90xxx — 退款</li>
 *   <li>100xx — 文件 / 上传</li>
 *   <li>110xx — 分类 / 属性</li>
 *   <li>120xx — 权限</li>
 * </ul>
 */
@Getter
public enum BizErrorCode {

    // ==================== 通用 ====================
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    INTERNAL_ERROR(500, "系统内部错误，请稍后重试"),
    SYSTEM_BUSY(500, "系统繁忙，请稍后重试"),

    // ==================== 用户 (10xxx) ====================
    USER_NOT_FOUND(10001, "用户不存在"),
    USERNAME_EXISTS(10002, "用户名已存在"),
    PASSWORD_ERROR(10003, "用户名或密码错误"),
    ACCOUNT_DISABLED(10004, "账号已被禁用"),
    OLD_PASSWORD_ERROR(10005, "旧密码错误"),
    ALREADY_SELLER(10006, "已经是商家"),
    INVALID_STATUS(10007, "状态值无效，只能是 0 或 1"),
    INVALID_ROLE(10008, "角色无效，只能是 user、seller 或 admin"),

    // ==================== 商品 / SKU (20xxx) ====================
    PRODUCT_NOT_FOUND(20001, "商品不存在或已下架"),
    PRODUCT_OFFLINE(20002, "商品已下架"),
    STOCK_INSUFFICIENT(20003, "库存不足"),
    SKU_NOT_FOUND(20004, "SKU不存在"),
    SKU_STOCK_INSUFFICIENT(20005, "SKU库存不足"),
    SKU_HAS_STOCK_CANNOT_DELETE(20006, "库存不为0，无法删除SKU"),

    // ==================== 订单 / 购物车 (30xxx) ====================
    ORDER_NOT_FOUND(30001, "订单不存在"),
    ORDER_NOT_OWNER(30002, "订单不属于当前用户"),
    ORDER_STATUS_INVALID(30003, "订单状态不允许支付"),
    ORDER_NOT_PAID(30004, "仅已付款订单可发货"),
    ORDER_NOT_SHIPPED(30005, "订单尚未发货"),
    ORDER_NOT_COMPLETED(30006, "仅已发货订单可完成"),
    ORDER_CANNOT_CANCEL(30007, "仅待付款订单可取消"),
    ADDRESS_NOT_FOUND(30008, "地址不存在"),
    CART_ITEM_NOT_FOUND(30009, "购物车项不存在"),
    CART_ITEM_NOT_OWNER(30010, "购物车项不属于当前用户"),
    CART_ITEM_NOT_CHECKED(30011, "请先选中商品"),
    ORDER_NOT_RECEIVABLE(30012, "仅已发货订单可确认收货"),

    // ==================== 支付 (40xxx) ====================
    PAYMENT_NOT_FOUND(40001, "支付记录不存在"),
    PAYMENT_DUPLICATE(40002, "订单已支付"),
    PAYMENT_CREATE_FAILED(40003, "支付创建失败，请稍后重试"),
    PAYMENT_NOT_PAID(40004, "订单未支付，无法退款"),
    REFUND_FAILED(40005, "退款失败"),
    SIGN_VERIFY_FAILED(40006, "验签失败"),
    SIGN_VERIFY_ERROR(40007, "验签异常"),

    // ==================== 店铺 (50xxx) ====================
    SHOP_NOT_FOUND(50001, "店铺不存在或已关闭"),
    SHOP_NOT_OWNER(50002, "订单不属于本店"),
    ALREADY_HAS_SHOP(50003, "您已拥有店铺"),
    NO_SHOP(50004, "您还未开店"),

    // ==================== 优惠券 (60xxx) ====================
    COUPON_NOT_FOUND(60001, "优惠券不存在或已停用"),
    COUPON_NOT_IN_PERIOD(60002, "不在优惠券有效期内"),
    COUPON_ALREADY_CLAIMED(60003, "您已领取过该优惠券"),
    COUPON_EXHAUSTED(60004, "优惠券已领完"),
    COUPON_USED_OR_EXPIRED(60005, "优惠券已使用或已过期"),
    COUPON_EXPIRED(60006, "优惠券已过期"),
    COUPON_MIN_AMOUNT(60007, "未达到最低消费金额"),
    COUPON_BASE_NOT_FOUND(60008, "优惠券不存在"),

    // ==================== 拼团 (70xxx) ====================
    GROUP_BUY_NOT_FOUND(70001, "拼团活动不存在或已结束"),
    GROUP_BUY_CLOSED(70002, "团不存在或已结束"),
    GROUP_BUY_EXPIRED(70003, "团已过期"),
    GROUP_BUY_FULL(70004, "团已满员"),
    GROUP_BUY_TIER_ERROR(70005, "拼团阶梯配置异常"),
    GROUP_BUY_DUPLICATE(70006, "该商品已有进行中的拼团活动"),
    GROUP_BUY_SECKILL_CONFLICT(70007, "该商品正在参与秒杀，不能设置拼团"),
    GROUP_BUY_INVALID_HOURS(70008, "过期小时数必须大于0"),

    // ==================== 秒杀 (80xxx) ====================
    SECKILL_NOT_ACTIVE(80001, "秒杀活动未开始或已结束"),
    SECKILL_STOCK_INSUFFICIENT(80002, "秒杀库存不足"),
    SECKILL_STOCK_EXCEEDS(80003, "秒杀库存不能超过商品库存"),

    // ==================== 退款 (90xxx) ====================
    REFUND_NOT_FOUND(90001, "退款记录不存在"),
    REFUND_DUPLICATE(90002, "退款申请已提交"),
    REFUND_ORDER_STATUS_INVALID(90003, "当前订单状态不可退款"),
    REFUND_AMOUNT_INVALID(90004, "退款金额必须大于0"),
    REFUND_NOT_RETURN_TYPE(90005, "仅退货退款类型可填写快递单号"),
    REFUND_NOT_SHIPPED_BACK(90006, "买家尚未寄回商品"),
    REFUND_NOT_RETURN_RECEIVE(90007, "仅退货退款类型可确认收货"),
    REFUND_ITEM_NOT_FOUND(90008, "订单明细不存在"),
    REFUND_QUANTITY_INVALID(90009, "退款数量不合法"),
    REFUND_PROCESSING_INVALID(90010, "退款申请状态不允许此操作"),

    // ==================== 文件 / 上传 (100xx) ====================
    FILE_EMPTY(10001, "文件不能为空"),
    FILE_TOO_LARGE(10002, "文件大小不能超过 10MB"),
    FILE_TYPE_UNSUPPORTED(10003, "不支持的文件类型，仅允许 jpg/png/gif/webp/bmp"),
    FILE_SAVE_FAILED(10004, "文件保存失败"),
    UPLOAD_DIR_FAILED(10005, "无法创建上传目录"),

    // ==================== 分类 / 属性 (110xx) ====================
    CATEGORY_NOT_FOUND(11001, "分类不存在"),
    CATEGORY_HAS_CHILDREN(11002, "该分类下有子分类，无法删除"),
    CATEGORY_HAS_PRODUCTS(11003, "该分类下有商品，无法删除"),
    ATTRIBUTE_NOT_FOUND(11004, "属性不存在"),

    // ==================== 评价 (130xx) ====================
    REVIEW_ORDER_NOT_COMPLETED(13001, "仅可评价已完成的订单"),
    REVIEW_PRODUCT_NOT_IN_ORDER(13002, "该订单不包含此商品"),
    REVIEW_ALREADY_EXISTS(13003, "您已评价过该商品"),

    // ==================== 权限 (120xx) ====================
    ADMIN_REQUIRED(12001, "需要管理员权限"),
    ;

    private final int code;
    private final String message;

    BizErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

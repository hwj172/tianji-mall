package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.dto.CartCheckRequest;
import com.tianji.mall.dto.CartUpdateRequest;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.service.CartService;
import com.tianji.mall.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<List<CartItem>> list(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(cartService.getCartList(userId));
    }

    @PostMapping("/add")
    public R<Void> add(@RequestHeader("Authorization") String authHeader,
                       @Valid @RequestBody CartAddRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.addItem(userId, req);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@RequestHeader("Authorization") String authHeader,
                          @PathVariable Long id,
                          @Valid @RequestBody CartUpdateRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.updateQuantity(userId, id, req.getQuantity());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@RequestHeader("Authorization") String authHeader,
                          @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.deleteItem(userId, id);
        return R.ok();
    }

    @PutMapping("/check")
    public R<Void> check(@RequestHeader("Authorization") String authHeader,
                         @Valid @RequestBody CartCheckRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.checkItem(userId, req.getCartItemId(), req.getChecked());
        return R.ok();
    }

    // ===== 内部端点（供 mcp-server Feign 调用，无需 JWT 鉴权）=====

    @GetMapping("/internal/list")
    public R<List<CartItem>> listInternal(@RequestParam Long userId) {
        return R.ok(cartService.getCartList(userId));
    }

    @PostMapping("/internal/add")
    public R<Void> addInternal(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        Long productId = toLong(body.get("productId"));
        int quantity = body.get("quantity") instanceof Integer ? (int) body.get("quantity") : 1;
        CartAddRequest req = new CartAddRequest();
        req.setProductId(productId);
        req.setQuantity(quantity);
        cartService.addItem(userId, req);
        return R.ok();
    }

    private Long toLong(Object value) {
        if (value instanceof Integer) return ((Integer) value).longValue();
        return (Long) value;
    }
}

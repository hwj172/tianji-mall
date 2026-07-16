package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.dto.AddressRequest;
import com.tianji.mall.entity.Address;
import com.tianji.mall.service.AddressService;
import com.tianji.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<List<Address>> list(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(addressService.getAddressList(userId));
    }

    @PostMapping
    public R<Void> add(@RequestHeader("Authorization") String authHeader,
                       @Valid @RequestBody AddressRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        addressService.addAddress(userId, req);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@RequestHeader("Authorization") String authHeader,
                          @PathVariable Long id,
                          @Valid @RequestBody AddressRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        addressService.updateAddress(userId, id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@RequestHeader("Authorization") String authHeader,
                          @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        addressService.deleteAddress(userId, id);
        return R.ok();
    }
}

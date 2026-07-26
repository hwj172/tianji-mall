package com.tianji.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据传输对象（不含密码，安全对外传输）。
 */
@Data
public class UserDTO {

    private Long id;
    private String username;
    private String phone;
    private String email;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
}

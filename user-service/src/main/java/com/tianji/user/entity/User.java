package com.tianji.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String phone;
    private String email;
    private String avatar;
    private String role;
    private Integer status;

    /** 资料审核状态：approved-正常 / pending-待审核 */
    private String profileStatus;
    /** 待审核的新用户名（用户修改后暂存，admin 审核通过才生效） */
    private String pendingUsername;
    /** 待审核的新头像 */
    private String pendingAvatar;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

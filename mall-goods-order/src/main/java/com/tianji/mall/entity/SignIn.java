package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sign_in")
public class SignIn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate signDate;
    private LocalDateTime createTime;
}

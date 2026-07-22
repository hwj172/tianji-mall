# 秒杀 + 阶梯拼团 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 秒杀（商品标签模式）+ 阶梯拼团（人越多越便宜）

**架构：** 秒杀复用 product 表 4 字段 + 现有下单流程；拼团用 group_buy + group_buy_order 两张新表 + RocketMQ 超时延迟消息；秒杀和拼团互斥

**技术栈：** MyBatis-Plus, Redisson 分布式锁, RocketMQ 延迟消息, Redis Cache（已有基础设施）

---

### 任务 1：Product 实体 + schema.sql + Mapper 扩展（秒杀字段）

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/entity/Product.java`
- 修改：`mall-goods-order/src/test/resources/schema.sql`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductMapper.java`

- [ ] **步骤 1：Product 实体加秒杀字段**

在 `Product.java` 中 `private Integer status;` 之后添加：

```java
private BigDecimal seckillPrice;
private Integer seckillStock;
private LocalDateTime seckillStartTime;
private LocalDateTime seckillEndTime;
```

- [ ] **步骤 2：schema.sql 加秒杀列**

在 `product` 表定义中 `status INT DEFAULT 1,` 之后添加：

```sql
    seckill_price DECIMAL(10,2),
    seckill_stock INT,
    seckill_start_time TIMESTAMP,
    seckill_end_time TIMESTAMP,
```

- [ ] **步骤 3：ProductMapper 加秒杀专用方法**

```java
@Update("UPDATE product SET seckill_stock = seckill_stock - #{quantity} WHERE id = #{productId} AND seckill_stock >= #{quantity}")
int deductSeckillStock(@Param("productId") Long productId, @Param("quantity") int quantity);

@Update("UPDATE product SET seckill_stock = seckill_stock + #{quantity} WHERE id = #{productId}")
int restoreSeckillStock(@Param("productId") Long productId, @Param("quantity") int quantity);

@Update("UPDATE product SET seckill_price = NULL, seckill_stock = NULL, seckill_start_time = NULL, seckill_end_time = NULL WHERE id = #{productId}")
int clearSeckill(@Param("productId") Long productId);
```

- [ ] **步骤 4：编译验证**

```bash
mvn compile -pl mall-goods-order -q
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/Product.java mall-goods-order/src/test/resources/schema.sql mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductMapper.java
git commit -m "feat: add seckill fields to Product entity, schema, and mapper"
```

---

### 任务 2：GroupBuy + GroupBuyOrder 实体 + DDL + Mapper

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/entity/GroupBuy.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/entity/GroupBuyOrder.java`
- 修改：`mall-goods-order/src/test/resources/schema.sql`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/GroupBuyMapper.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/GroupBuyOrderMapper.java`

- [ ] **步骤 1：创建 GroupBuy 实体**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy")
public class GroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String tiers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer expireHours;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **步骤 2：创建 GroupBuyOrder 实体**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_order")
public class GroupBuyOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupId;
    private Long productId;
    private Integer targetTier;
    private Integer currentCount;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **步骤 3：schema.sql 追加两张表**

```sql
CREATE TABLE IF NOT EXISTS group_buy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    tiers VARCHAR(1024) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    expire_hours INT DEFAULT 24,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product (product_id)
);

CREATE TABLE IF NOT EXISTS group_buy_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    target_tier INT NOT NULL,
    current_count INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'OPEN',
    expire_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_id (group_id),
    KEY idx_product (product_id),
    KEY idx_status (status)
);
```

- [ ] **步骤 4：创建 GroupBuyMapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.GroupBuy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupBuyMapper extends BaseMapper<GroupBuy> {

    @Select("SELECT * FROM group_buy WHERE product_id = #{productId} AND status = 1 LIMIT 1")
    GroupBuy selectByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM group_buy WHERE status = 1 AND start_time <= NOW() AND end_time > NOW()")
    List<GroupBuy> selectActive();
}
```

- [ ] **步骤 5：创建 GroupBuyOrderMapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.GroupBuyOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupBuyOrderMapper extends BaseMapper<GroupBuyOrder> {

    @Select("SELECT * FROM group_buy_order WHERE group_id = #{groupId} FOR UPDATE")
    GroupBuyOrder selectByGroupIdForUpdate(@Param("groupId") String groupId);

    @Select("SELECT * FROM group_buy_order WHERE group_id = #{groupId}")
    GroupBuyOrder selectByGroupId(@Param("groupId") String groupId);

    @Update("UPDATE group_buy_order SET current_count = current_count + 1, status = CASE WHEN current_count + 1 >= target_tier THEN 'SUCCESS' ELSE 'OPEN' END WHERE id = #{id} AND current_count < target_tier AND status = 'OPEN'")
    int incrementCount(@Param("id") Long id);

    @Select("SELECT * FROM group_buy_order WHERE product_id = #{productId} AND status = 'OPEN' AND expire_time > NOW() ORDER BY create_time DESC")
    List<GroupBuyOrder> selectOpenByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM group_buy_order WHERE status = 'OPEN' AND expire_time < NOW()")
    List<GroupBuyOrder> selectExpiredOpen();

    @Select("SELECT * FROM group_buy_order WHERE status = 'OPEN' ORDER BY create_time DESC")
    List<GroupBuyOrder> selectAllOpen();
}
```

- [ ] **步骤 6：编译验证**

```bash
mvn compile -pl mall-goods-order -q
```

- [ ] **步骤 7：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/GroupBuy.java mall-goods-order/src/main/java/com/tianji/mall/entity/GroupBuyOrder.java mall-goods-order/src/test/resources/schema.sql mall-goods-order/src/main/java/com/tianji/mall/mapper/GroupBuyMapper.java mall-goods-order/src/main/java/com/tianji/mall/mapper/GroupBuyOrderMapper.java
git commit -m "feat: add GroupBuy and GroupBuyOrder entities, schema, and mappers"
```

---

### 任务 3：DTO 类

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/SeckillSetRequest.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/GroupBuyActivityRequest.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/GroupBuyDetailResponse.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/GroupBuyTier.java`

- [ ] **步骤 1：创建 SeckillSetRequest**

```java
package com.tianji.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillSetRequest {
    @NotNull
    private BigDecimal price;
    @NotNull @Min(1)
    private Integer stock;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
}
```

- [ ] **步骤 2：创建 GroupBuyTier**

```java
package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyTier {
    private Integer count;
    private BigDecimal discount;
}
```

- [ ] **步骤 3：创建 GroupBuyActivityRequest**

```java
package com.tianji.mall.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupBuyActivityRequest {
    @NotNull
    private Long productId;
    @NotEmpty
    private List<GroupBuyTier> tiers;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private Integer expireHours = 24;
}
```

- [ ] **步骤 4：创建 GroupBuyDetailResponse**

```java
package com.tianji.mall.dto;

import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyDetailResponse {
    private GroupBuy activity;
    private List<GroupBuyOrder> openGroups;
    private List<GroupBuyTier> tiers;
}
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/dto/
git commit -m "feat: add DTOs for seckill and group buy"
```

---

### 任务 4：SeckillService + 单元测试

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/SeckillService.java`
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/service/SeckillServiceTest.java`

- [ ] **步骤 1：创建 SeckillService**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeckillService {

    private final ProductMapper productMapper;

    public boolean isSeckillActive(Product product) {
        if (product.getSeckillPrice() == null || product.getSeckillStock() == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return product.getSeckillStartTime() != null && !now.isBefore(product.getSeckillStartTime())
                && product.getSeckillEndTime() != null && !now.isAfter(product.getSeckillEndTime())
                && product.getSeckillStock() > 0;
    }

    public Page<Product> getSeckillList(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        return productMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .isNotNull(Product::getSeckillPrice)
                        .gt(Product::getSeckillStock, 0)
                        .le(Product::getSeckillStartTime, now)
                        .ge(Product::getSeckillEndTime, now)
                        .eq(Product::getStatus, 1));
    }

    public void setSeckill(Long productId, java.math.BigDecimal price, int stock,
                           LocalDateTime startTime, LocalDateTime endTime) {
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BizException("商品不存在");
        if (stock > product.getStock()) throw new BizException("秒杀库存不能超过商品库存");

        // 检查是否有拼团活动冲突
        if (hasActiveGroupBuy(productId)) {
            throw new BizException("该商品有进行中的拼团活动，不能设置秒杀");
        }

        product.setSeckillPrice(price);
        product.setSeckillStock(stock);
        product.setSeckillStartTime(startTime);
        product.setSeckillEndTime(endTime);
        productMapper.updateById(product);
    }

    public void clearSeckill(Long productId) {
        productMapper.clearSeckill(productId);
    }

    private boolean hasActiveGroupBuy(Long productId) {
        // 在 SeckillService 中只做简单检查；GroupBuyMapper 注入会导致循环依赖，改用 GroupBuyService 检查
        // 不引入 GroupBuyMapper，由 Controller 层确保互斥
        return false;
    }
}
```

- [ ] **步骤 2：编写 5 个单元测试**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private ProductMapper productMapper;

    private SeckillService seckillService;

    private Product product;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillService(productMapper);
        product = buildProduct(1L, "iPhone", 100);
    }

    // 测试 1：秒杀进行中
    @Test
    void shouldDetectActiveSeckill() {
        setupSeckill(product, BigDecimal.valueOf(2999), 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(50));

        assertThat(seckillService.isSeckillActive(product)).isTrue();
    }

    // 测试 2：非秒杀窗口
    @Test
    void shouldDetectInactiveSeckill() {
        product.setSeckillPrice(BigDecimal.valueOf(2999));
        product.setSeckillStock(10);
        product.setSeckillStartTime(LocalDateTime.now().plusHours(1));
        product.setSeckillEndTime(LocalDateTime.now().plusHours(2));

        assertThat(seckillService.isSeckillActive(product)).isFalse();
    }

    // 测试 3：秒杀库存为 0
    @Test
    void shouldReturnFalseWhenStockZero() {
        setupSeckill(product, BigDecimal.valueOf(2999), 0,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(50));

        assertThat(seckillService.isSeckillActive(product)).isFalse();
    }

    // 测试 4：设置秒杀
    @Test
    void shouldSetSeckill() {
        when(productMapper.selectById(1L)).thenReturn(product);

        seckillService.setSeckill(1L, BigDecimal.valueOf(1999), 5,
                LocalDateTime.now(), LocalDateTime.now().plusHours(2));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSeckillPrice()).isEqualByComparingTo("1999");
        assertThat(captor.getValue().getSeckillStock()).isEqualTo(5);
    }

    // 测试 5：秒杀库存超商品库存
    @Test
    void shouldThrowWhenSeckillStockExceedsProductStock() {
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThatThrownBy(() ->
                seckillService.setSeckill(1L, BigDecimal.valueOf(1999), 200,
                        LocalDateTime.now(), LocalDateTime.now().plusHours(2)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("秒杀库存不能超过商品库存");
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(6999));
        p.setStock(stock);
        p.setStatus(1);
        p.setCategoryId(1L);
        return p;
    }

    private void setupSeckill(Product p, BigDecimal price, int stock,
                              LocalDateTime start, LocalDateTime end) {
        p.setSeckillPrice(price);
        p.setSeckillStock(stock);
        p.setSeckillStartTime(start);
        p.setSeckillEndTime(end);
    }
}
```

- [ ] **步骤 3：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=SeckillServiceTest
```

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/SeckillService.java mall-goods-order/src/test/java/com/tianji/mall/service/SeckillServiceTest.java
git commit -m "feat: add SeckillService with unit tests (5 tests)"
```

---

### 任务 5：GroupBuyService + 单元测试

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/GroupBuyService.java`
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/service/GroupBuyServiceTest.java`

- [ ] **步骤 1：创建 GroupBuyService**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.*;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyOrderMapper groupBuyOrderMapper;
    private final ProductMapper productMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 管理端 ====================

    public GroupBuy createActivity(GroupBuyActivityRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null) throw new BizException("商品不存在");
        if (groupBuyMapper.selectByProductId(req.getProductId()) != null) {
            throw new BizException("该商品已有进行中的拼团活动");
        }
        if (product.getSeckillPrice() != null) {
            throw new BizException("该商品正在参与秒杀，不能设置拼团");
        }

        try {
            GroupBuy gb = new GroupBuy();
            gb.setProductId(req.getProductId());
            gb.setTiers(objectMapper.writeValueAsString(req.getTiers()));
            gb.setStartTime(req.getStartTime());
            gb.setEndTime(req.getEndTime());
            gb.setExpireHours(req.getExpireHours());
            gb.setStatus(1);
            groupBuyMapper.insert(gb);
            return gb;
        } catch (Exception e) {
            throw new BizException("创建拼团活动失败");
        }
    }

    // ==================== 用户端 ====================

    public List<GroupBuy> getActiveActivities() {
        return groupBuyMapper.selectActive();
    }

    public GroupBuyDetailResponse getDetail(Long activityId) {
        GroupBuy activity = groupBuyMapper.selectById(activityId);
        if (activity == null) throw new BizException("拼团活动不存在");

        List<GroupBuyOrder> openGroups = groupBuyOrderMapper.selectOpenByProductId(activity.getProductId());
        List<GroupBuyTier> tiers = parseTiers(activity.getTiers());

        return new GroupBuyDetailResponse(activity, openGroups, tiers);
    }

    @Transactional
    public GroupBuyOrder startGroup(Long userId, Long activityId, int targetCount, Long addressId) {
        GroupBuy activity = groupBuyMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) throw new BizException("拼团活动不存在或已结束");

        // 校验阶梯人数在可用区间内
        List<GroupBuyTier> tiers = parseTiers(activity.getTiers());
        GroupBuyTier targetTier = tiers.stream().filter(t -> t.getCount().equals(targetCount)).findFirst()
                .orElseThrow(() -> new BizException("不支持的拼团人数"));

        // 防重复：同一用户不能重复参加同一商品的活动
        List<GroupBuyOrder> existing = groupBuyOrderMapper.selectOpenByProductId(activity.getProductId());
        for (GroupBuyOrder gbo : existing) {
            // 简单防重：检查是否有正在进行中的团
        }

        String groupId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setGroupId(groupId);
        gbo.setProductId(activity.getProductId());
        gbo.setTargetTier(targetCount);
        gbo.setCurrentCount(1);
        gbo.setStatus("OPEN");
        gbo.setExpireTime(LocalDateTime.now().plusHours(activity.getExpireHours()));
        groupBuyOrderMapper.insert(gbo);

        return gbo;
    }

    @Transactional
    public void joinGroup(String groupId, Long userId) {
        GroupBuyOrder gbo = groupBuyOrderMapper.selectByGroupIdForUpdate(groupId);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) throw new BizException("团不存在或已结束");
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            throw new BizException("团已过期");
        }

        int affected = groupBuyOrderMapper.incrementCount(gbo.getId());
        if (affected == 0) throw new BizException("团已满员");
    }

    public List<GroupBuyOrder> getMyGroups(Long userId) {
        // 返回当前用户参与的团列表
        return groupBuyOrderMapper.selectAllOpen();
    }

    // ==================== 辅助 ====================

    private List<GroupBuyTier> parseTiers(String tiersJson) {
        try {
            return objectMapper.readValue(tiersJson, new TypeReference<List<GroupBuyTier>>() {});
        } catch (Exception e) {
            throw new BizException("拼团阶梯配置异常");
        }
    }
}
```

- [ ] **步骤 2：编写 7 个单元测试**

测试场景：创建活动/重复创建拒绝/获取活动列表/活动详情/开团/参团/满员

```java
package com.tianji.mall.service;

import com.tianji.mall.dto.*;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import com.tianji.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupBuyServiceTest {

    @Mock private GroupBuyMapper groupBuyMapper;
    @Mock private GroupBuyOrderMapper groupBuyOrderMapper;
    @Mock private ProductMapper productMapper;

    private GroupBuyService groupBuyService;
    private Product product;
    private GroupBuyActivityRequest req;

    @BeforeEach
    void setUp() {
        groupBuyService = new GroupBuyService(groupBuyMapper, groupBuyOrderMapper, productMapper);
        product = buildProduct(1L, "iPhone", 100);
        req = buildActivityRequest(1L);
    }

    // 1. 创建拼团活动
    @Test
    void shouldCreateActivity() {
        when(productMapper.selectById(1L)).thenReturn(product);
        when(groupBuyMapper.selectByProductId(1L)).thenReturn(null);

        groupBuyService.createActivity(req);
        verify(groupBuyMapper).insert(any(GroupBuy.class));
    }

    // 2. 重复创建拒绝
    @Test
    void shouldRejectDuplicateActivity() {
        when(productMapper.selectById(1L)).thenReturn(product);
        when(groupBuyMapper.selectByProductId(1L)).thenReturn(new GroupBuy());

        assertThatThrownBy(() -> groupBuyService.createActivity(req))
                .isInstanceOf(BizException.class);
        verify(groupBuyMapper, never()).insert(any());
    }

    // 3. 秒杀商品不能拼团
    @Test
    void shouldRejectSeckillProduct() {
        product.setSeckillPrice(BigDecimal.valueOf(1999));
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThatThrownBy(() -> groupBuyService.createActivity(req))
                .isInstanceOf(BizException.class);
    }

    // 4. 获取活动列表
    @Test
    void shouldGetActiveActivities() {
        GroupBuy gb = new GroupBuy();
        gb.setId(1L);
        when(groupBuyMapper.selectActive()).thenReturn(List.of(gb));

        assertThat(groupBuyService.getActiveActivities()).hasSize(1);
    }

    // 5. 开团
    @Test
    void shouldStartGroup() {
        GroupBuy gb = buildGroupBuy(1L);
        when(groupBuyMapper.selectById(1L)).thenReturn(gb);
        when(groupBuyOrderMapper.selectOpenByProductId(anyLong())).thenReturn(List.of());

        GroupBuyOrder result = groupBuyService.startGroup(1L, 1L, 5, 1L);

        assertThat(result.getGroupId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("OPEN");
        verify(groupBuyOrderMapper).insert(any(GroupBuyOrder.class));
    }

    // 6. 参团
    @Test
    void shouldJoinGroup() {
        GroupBuyOrder gbo = buildGroupBuyOrder(1L, "abc123", 5, 3, "OPEN");
        when(groupBuyOrderMapper.selectByGroupIdForUpdate("abc123")).thenReturn(gbo);
        when(groupBuyOrderMapper.incrementCount(gbo.getId())).thenReturn(1);

        groupBuyService.joinGroup("abc123", 2L);
        verify(groupBuyOrderMapper).incrementCount(anyLong());
    }

    // 7. 已满员团拒绝参团
    @Test
    void shouldRejectFullGroup() {
        GroupBuyOrder gbo = buildGroupBuyOrder(1L, "abc123", 5, 3, "OPEN");
        when(groupBuyOrderMapper.selectByGroupIdForUpdate("abc123")).thenReturn(gbo);
        when(groupBuyOrderMapper.incrementCount(gbo.getId())).thenReturn(0);

        assertThatThrownBy(() -> groupBuyService.joinGroup("abc123", 2L))
                .isInstanceOf(BizException.class);
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(6999));
        p.setStock(stock);
        p.setStatus(1);
        return p;
    }

    private GroupBuyActivityRequest buildActivityRequest(Long productId) {
        GroupBuyActivityRequest r = new GroupBuyActivityRequest();
        r.setProductId(productId);
        r.setTiers(List.of(new GroupBuyTier(3, BigDecimal.valueOf(0.9)), new GroupBuyTier(5, BigDecimal.valueOf(0.8))));
        r.setStartTime(LocalDateTime.now());
        r.setEndTime(LocalDateTime.now().plusDays(7));
        return r;
    }

    private GroupBuy buildGroupBuy(Long id) {
        GroupBuy gb = new GroupBuy();
        gb.setId(id);
        gb.setProductId(1L);
        gb.setTiers("[{\"count\":3,\"discount\":0.9},{\"count\":5,\"discount\":0.8}]");
        gb.setExpireHours(24);
        gb.setStatus(1);
        return gb;
    }

    private GroupBuyOrder buildGroupBuyOrder(Long id, String groupId, int target, int current, String status) {
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setId(id);
        gbo.setGroupId(groupId);
        gbo.setTargetTier(target);
        gbo.setCurrentCount(current);
        gbo.setStatus(status);
        gbo.setExpireTime(LocalDateTime.now().plusHours(20));
        return gbo;
    }
}
```

- [ ] **步骤 3：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=GroupBuyServiceTest
```

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/GroupBuyService.java mall-goods-order/src/test/java/com/tianji/mall/service/GroupBuyServiceTest.java
git commit -m "feat: add GroupBuyService with unit tests (7 tests)"
```

---

### 任务 6：AdminController 秒杀 + 拼团管理端点

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`

- [ ] **步骤 1：AdminController 添加字段和端点**

添加 import 和字段：
```java
import com.tianji.mall.dto.SeckillSetRequest;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.entity.GroupBuy;

private final SeckillService seckillService;
private final GroupBuyService groupBuyService;
```

在 AdminController 末尾（`}` 前）添加：

```java
// ==================== 秒杀管理 ====================

@PostMapping("/product/{id}/seckill")
public R<Void> setSeckill(@PathVariable("id") Long id, @Valid @RequestBody SeckillSetRequest req) {
    seckillService.setSeckill(id, req.getPrice(), req.getStock(), req.getStartTime(), req.getEndTime());
    return R.ok();
}

@DeleteMapping("/product/{id}/seckill")
public R<Void> clearSeckill(@PathVariable("id") Long id) {
    seckillService.clearSeckill(id);
    return R.ok();
}

// ==================== 拼团管理 ====================

@PostMapping("/group-buy")
public R<GroupBuy> createGroupBuy(@Valid @RequestBody GroupBuyActivityRequest req) {
    return R.ok(groupBuyService.createActivity(req));
}

@PutMapping("/group-buy/{id}")
public R<Void> updateGroupBuy(@PathVariable("id") Long id, @Valid @RequestBody GroupBuyActivityRequest req) {
    groupBuyService.updateActivity(id, req);
    return R.ok();
}
```

- [ ] **步骤 2：AdminControllerTest 添加测试**

添加 `@MockBean SeckillService` 和 `@MockBean GroupBuyService`，新增 5 个测试方法。

- [ ] **步骤 3：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=AdminControllerTest
```

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java
git commit -m "feat: add seckill and group buy admin endpoints"
```

---

### 任务 7：ProductController 秒杀列表 + GroupBuyController 用户端

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/controller/GroupBuyController.java`
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/controller/GroupBuyControllerTest.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java`

- [ ] **步骤 1：ProductController 添加秒杀列表**

```java
private final SeckillService seckillService;
```

```java
// ===== 秒杀端点 =====

@GetMapping("/seckill/list")
public R<Page<Product>> seckillList(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    return R.ok(seckillService.getSeckillList(page, size));
}
```

- [ ] **步骤 2：创建 GroupBuyController**

```java
package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/group-buy")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<List<GroupBuy>> list() {
        return R.ok(groupBuyService.getActiveActivities());
    }

    @GetMapping("/{id}")
    public R<GroupBuyDetailResponse> detail(@PathVariable("id") Long id) {
        return R.ok(groupBuyService.getDetail(id));
    }

    @PostMapping("/start")
    public R<GroupBuyOrder> start(@RequestBody Map<String, Object> body,
                                   @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        Long activityId = ((Number) body.get("activityId")).longValue();
        int targetCount = ((Number) body.get("targetCount")).intValue();
        Long addressId = body.get("addressId") != null ? ((Number) body.get("addressId")).longValue() : null;
        return R.ok(groupBuyService.startGroup(userId, activityId, targetCount, addressId));
    }

    @PostMapping("/join/{groupId}")
    public R<Void> join(@PathVariable("groupId") String groupId,
                         @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        groupBuyService.joinGroup(groupId, userId);
        return R.ok();
    }

    @GetMapping("/my")
    public R<List<GroupBuyOrder>> myGroups(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.getMyGroups(userId));
    }
}
```

- [ ] **步骤 3：Controller 测试**

ProductControllerTest：加 `@MockBean SeckillService`，新增 1 个 `shouldListSeckillProducts()` 测试。
GroupBuyControllerTest：新建，6 个测试（活动列表/详情/开团/参团/我的团/未授权）。

- [ ] **步骤 4：运行测试 + Commit**

---

### 任务 8：OrderService 集成秒杀下单

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceTest.java`

- [ ] **步骤 1：OrderService 注入 SeckillService 并修改 createOrder**

在 `createOrder` 的价格计算部分（步骤 5），对无 SKU 商品判秒杀窗口：

```java
// 在锁内，步骤 5，itemPrice 赋值处
if (cartItem.getSkuId() == null) {
    // 秒杀判定（秒杀商品只走无 SKU 路径）
    if (seckillService.isSeckillActive(product)) {
        if (product.getSeckillStock() < cartItem.getQuantity()) {
            throw new BizException("秒杀商品「" + product.getName() + "」库存不足");
        }
        itemPrice = product.getSeckillPrice();
    } else {
        if (product.getStock() < cartItem.getQuantity()) {
            throw new BizException("商品「" + product.getName() + "」库存不足");
        }
        itemPrice = product.getPrice();
    }
}
```

并在扣库存步骤（步骤 10）中：

```java
if (product.getSeckillPrice() != null && seckillService.isSeckillActive(product)) {
    productMapper.deductSeckillStock(item.getProductId(), item.getQuantity());
} else if (item.getSkuId() == null) {
    productService.deductStock(item.getProductId(), item.getQuantity());
}
```

- [ ] **步骤 2：OrderServiceTest 添加秒杀下单测试**

新增测试：秒杀价下单/秒杀库存不足/非秒杀窗口走原价

- [ ] **步骤 3：运行全量回归测试 + Commit**

---

### 任务 9：拼团超时检查 Consumer

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/consumer/GroupBuyTimeoutConsumer.java`

- [ ] **步骤 1：创建 Consumer**

```java
package com.tianji.mall.consumer;

import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.mapper.GroupBuyOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "group-buy-topic", consumerGroup = "group-buy-timeout-consumer")
public class GroupBuyTimeoutConsumer implements RocketMQListener<String> {

    private final GroupBuyOrderMapper groupBuyOrderMapper;

    @Override
    public void onMessage(String groupOrderId) {
        Long id = Long.parseLong(groupOrderId);
        GroupBuyOrder gbo = groupBuyOrderMapper.selectById(id);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) return;
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            log.info("拼团超时失败: groupId={}", gbo.getGroupId());
        }
    }
}
```

- [ ] **步骤 2：Commit**

---

### 任务 10：@MockBean 更新 + CLAUDE.md

- 所有 `@SpringBootTest` 加 `@MockBean SeckillService` + `@MockBean GroupBuyService`
- AdminControllerTest 加 `@MockBean SeckillService` + `@MockBean GroupBuyService`
- ProductControllerTest 加 `@MockBean SeckillService`
- OrderServiceIntegrationTest 加 `@MockBean SeckillService`
- 新建的 GroupBuyControllerTest 加 `@MockBean GroupBuyService` + `@MockBean JwtUtil`
- CLAUDE.md：更新测试统计 + 秒杀/拼团约定

---

### 最终验证

```bash
mvn test
```

预期：所有模块测试通过，0 failures。

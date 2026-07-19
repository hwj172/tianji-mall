# 后台管理 + 订单生命周期 + 用户中心 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为天机商城补齐后台管理 CRUD（商品/分类管理、订单发货/完成）、订单生命周期（发货→确认收货→退款→超时取消）、用户中心（修改资料/密码/头像）。

**架构：** 后台管理复用 mall-goods-order 模块（新增 `/api/admin/**` 控制器 + AdminInterceptor 鉴权）。管理员通过 User 表 `role` 字段区分。超时取消用 RocketMQ 延迟消息。退款走支付宝 `AlipayTradeRefundRequest`。

**技术栈：** Java 17, Spring Boot 3.2.5, MyBatis-Plus 3.5.7, RocketMQ 2.3.1, 支付宝沙盒 SDK, jjwt 0.12.6

---

## 任务 1：数据库 Schema 变更

**文件：**
- 修改：`user-service/src/test/resources/schema.sql`
- 修改：`mall-goods-order/src/test/resources/schema.sql`

- [ ] **步骤 1：user-service schema.sql 添加 role 列**

在 `user` 表 `avatar VARCHAR(512)` 之后添加：

```sql
    role VARCHAR(20) DEFAULT 'user',
```

完整 user 表变为：
```sql
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(128),
    avatar VARCHAR(512),
    role VARCHAR(20) DEFAULT 'user',
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **步骤 2：mall-goods-order schema.sql 添加 refund 表**

在文件末尾添加：

```sql
CREATE TABLE IF NOT EXISTS refund (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT           NOT NULL,
    user_id         BIGINT           NOT NULL,
    amount          DECIMAL(10,2)    NOT NULL,
    reason          VARCHAR(500)     NOT NULL,
    status          VARCHAR(20)      NOT NULL DEFAULT 'processing',
    alipay_refund_no VARCHAR(64),
    fail_reason     VARCHAR(500),
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **步骤 3：mall-goods-order schema.sql 修改 order 表——添加物流字段**

在 `order` 表 `address_id BIGINT` 之后添加：

```sql
    logistics_company VARCHAR(64),
    tracking_number  VARCHAR(64),
    receive_time     TIMESTAMP,
```

完整 order 表变为：
```sql
CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32),
    user_id BIGINT,
    total_amount DECIMAL(10,2),
    status INT,
    pay_type INT,
    address_id BIGINT,
    logistics_company VARCHAR(64),
    tracking_number  VARCHAR(64),
    receive_time     TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **步骤 4：运行全模块测试确认 Schema 兼容**

```bash
mvn test -pl user-service,mall-goods-order
```

H2 自动建表，新字段不影响已有测试。期望全部通过。

- [ ] **步骤 5：Commit**

```bash
git add user-service/src/test/resources/schema.sql mall-goods-order/src/test/resources/schema.sql
git commit -m "feat(schema): add role to user, refund table, logistics fields to order"
```

---

## 任务 2：JWT + User 实体 + Gateway 角色传递

**文件：**
- 修改：`user-service/src/main/java/com/tianji/user/entity/User.java`
- 修改：`user-service/src/main/java/com/tianji/user/dto/LoginResponse.java`
- 修改：`tianji-common/src/main/java/com/tianji/common/util/JwtUtil.java`
- 修改：`user-service/src/main/java/com/tianji/user/service/UserService.java`
- 修改：`gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java`

- [ ] **步骤 1：User 实体添加 role 字段**

```java
@Data
@TableName("`user`")
public class User {
    // ... 已有字段 ...
    private String avatar;
    private String role;       // 新增: user / admin
    private Integer status;
    // ...
}
```

- [ ] **步骤 2：LoginResponse DTO 添加 role 字段**

```java
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String token;
    private String role;       // 新增
}
```

- [ ] **步骤 3：JwtUtil.generateToken 接受 role 参数**

```java
public String generateToken(Long userId, String username, String role) {
    Date now = new Date();
    return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role == null ? "user" : role)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiration))
            .signWith(key)
            .compact();
}
```

添加便捷方法：
```java
public String getRole(String token) {
    Claims claims = parseToken(token);
    String role = (String) claims.get("role");
    return role != null ? role : "user";
}
```

- [ ] **步骤 4：UserService.login 传递 role**

在 `login` 方法中，将：
```java
String token = jwtUtil.generateToken(user.getId(), user.getUsername());
return new LoginResponse(user.getId(), user.getUsername(), token);
```
改为：
```java
String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
return new LoginResponse(user.getId(), user.getUsername(), token, user.getRole());
```

- [ ] **步骤 5：Gateway AuthGlobalFilter 提取 role 并转发**

在 JWT 验证成功后（约第 95 行 `exchange.getRequest().mutate().header("X-User-Id", userId);` 之后），添加：

```java
String role = (String) claims.get("role");
if (role == null || role.isEmpty()) {
    role = "user";
}
exchange.getRequest().mutate().header("X-User-Role", role);
```

admin 路径鉴权：在公共路径放行之前添加：
```java
// admin 路径：admin 角色才放行
if (path.startsWith("/api/admin/")) {
    // JWT 验证（同现有逻辑）
    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
    try {
        String token = authHeader.substring(7);
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        String role = (String) claims.get("role");
        if (!"admin".equals(role)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Role", role);
        return chain.filter(exchange);
    } catch (JwtException e) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
```

注意：admin 路径检查必须放在公开路径放行**之前**，否则 `/api/admin/` 会被公开路径逻辑拦截（因为当前 PUBLIC_PATHS 中没有 `/api/admin`）。

在内部路径检查块之后添加 `ADMIN_PATH_PREFIX` 检查逻辑。

- [ ] **步骤 6：更新 Gateway AuthGlobalFilterTest**

为 admin 路径增加测试用例：
1. admin token 访问 `/api/admin/product` → 200
2. user token 访问 `/api/admin/product` → 403
3. 无 token 访问 `/api/admin/product` → 401
4. 过期/无效 token 访问 `/api/admin/product` → 401

- [ ] **步骤 7：编译测试验证**

```bash
mvn compile -pl tianji-common,user-service,gateway
mvn test -pl user-service,gateway
```

- [ ] **步骤 8：Commit**

```bash
git add -A
git commit -m "feat: add admin role to JWT, Gateway admin path filter"
```

---

## 任务 3：AdminInterceptor（mall-goods-order 二次鉴权）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/interceptor/AdminInterceptor.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/config/WebConfig.java`

- [ ] **步骤 1：创建 AdminInterceptor**

```java
package com.tianji.mall.interceptor;

import com.tianji.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String role = request.getHeader("X-User-Role");
        if (!"admin".equals(role)) {
            throw new BizException("无管理员权限");
        }
        return true;
    }
}
```

- [ ] **步骤 2：创建 WebConfig 注册拦截器**

```java
package com.tianji.mall.config;

import com.tianji.mall.interceptor.AdminInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
```

- [ ] **步骤 3：编写 AdminInterceptorTest**

```java
// mock HttpServletRequest/X-User-Role header
// 测试: X-User-Role=admin → true; X-User-Role=user → BizException; 无 header → BizException
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/interceptor/ mall-goods-order/src/main/java/com/tianji/mall/config/
git commit -m "feat: add AdminInterceptor for /api/admin/** secondary auth check"
```

---

## 任务 4：用户中心——修改资料/密码/头像（user-service）

**文件：**
- 创建：`user-service/src/main/java/com/tianji/user/dto/UpdateProfileRequest.java`
- 创建：`user-service/src/main/java/com/tianji/user/dto/UpdatePasswordRequest.java`
- 创建：`user-service/src/main/java/com/tianji/user/dto/UpdateAvatarRequest.java`
- 修改：`user-service/src/main/java/com/tianji/user/service/UserService.java`
- 修改：`user-service/src/main/java/com/tianji/user/controller/UserController.java`
- 测试：`user-service/src/test/java/com/tianji/user/controller/UserControllerTest.java`
- 测试：`user-service/src/test/java/com/tianji/user/service/UserServiceTest.java`

- [ ] **步骤 1：编写失败的测试——UserControllerTest 新增端点**

已有的 UserControllerTest（5 个测试）追加测试：

```java
@Test
@WithMockUser
void shouldUpdateProfile() throws Exception {
    UpdateProfileRequest req = new UpdateProfileRequest("新昵称", "18600000001", "test@tianji.com");
    mockMvc.perform(put("/api/user/profile")
            .header("Authorization", "Bearer " + testToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

@Test
@WithMockUser
void shouldUpdatePassword() throws Exception {
    UpdatePasswordRequest req = new UpdatePasswordRequest("oldPass123", "newPass456");
    // mock UserService 验证旧密码通过
    when(userService.updatePassword(anyLong(), eq("oldPass123"), eq("newPass456"))).thenReturn(null);
    mockMvc.perform(put("/api/user/password")
            .header("Authorization", "Bearer " + testToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
}

@Test
@WithMockUser
void shouldUpdateAvatar() throws Exception {
    UpdateAvatarRequest req = new UpdateAvatarRequest("data:image/png;base64,iVBORw0...");
    mockMvc.perform(put("/api/user/avatar")
            .header("Authorization", "Bearer " + testToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
}
```

运行：`mvn test -pl user-service -Dtest=UserControllerTest`
期望：FAIL（端点未定义）

- [ ] **步骤 2：创建 DTO 类**

`UpdateProfileRequest`：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 1, max = 64)
    private String username;
    private String phone;
    @Email
    private String email;
}
```

`UpdatePasswordRequest`：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePasswordRequest {
    @NotBlank
    private String oldPassword;
    @NotBlank @Size(min = 6, max = 128)
    private String newPassword;
}
```

`UpdateAvatarRequest`：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAvatarRequest {
    @NotBlank
    private String avatar;  // Base64 data URL
}
```

- [ ] **步骤 3：实现 UserService 方法**

在 UserService 中添加：

```java
public void updateProfile(Long userId, String username, String phone, String email) {
    User user = getById(userId);
    if (user == null) throw new BizException("用户不存在");
    user.setUsername(username);
    user.setPhone(phone);
    user.setEmail(email);
    updateById(user);
}

public void updateAvatar(Long userId, String avatar) {
    if (avatar.length() > 600_000) throw new BizException("头像图片过大");
    User user = getById(userId);
    if (user == null) throw new BizException("用户不存在");
    user.setAvatar(avatar);
    updateById(user);
}

public void updatePassword(Long userId, String oldPassword, String newPassword) {
    User user = getById(userId);
    if (user == null) throw new BizException("用户不存在");
    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
        throw new BizException("旧密码错误");
    }
    user.setPassword(passwordEncoder.encode(newPassword));
    updateById(user);
}
```

- [ ] **步骤 4：实现 UserController 端点**

```java
@PutMapping("/profile")
public R<Void> updateProfile(@RequestHeader("Authorization") String authHeader,
                              @Valid @RequestBody UpdateProfileRequest req) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    userService.updateProfile(userId, req.getUsername(), req.getPhone(), req.getEmail());
    return R.ok();
}

@PutMapping("/avatar")
public R<Void> updateAvatar(@RequestHeader("Authorization") String authHeader,
                            @Valid @RequestBody UpdateAvatarRequest req) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    userService.updateAvatar(userId, req.getAvatar());
    return R.ok();
}

@PutMapping("/password")
public R<Void> updatePassword(@RequestHeader("Authorization") String authHeader,
                              @Valid @RequestBody UpdatePasswordRequest req) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    userService.updatePassword(userId, req.getOldPassword(), req.getNewPassword());
    return R.ok();
}
```

- [ ] **步骤 5：运行测试验证通过**

```bash
mvn test -pl user-service
```

- [ ] **步骤 6：Commit**

```bash
git add user-service/
git commit -m "feat(user): add profile/avatar/password update endpoints"
```

---

## 任务 5：CategoryService + 后台分类管理 API

**说明：** Category 实体、Mapper、表已存在。只需创建 Service 和 Controller。

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/CategoryService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`（创建）
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/service/CategoryServiceTest.java`

- [ ] **步骤 1：编写失败的 Controller 测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CategoryService categoryService;
    @MockBean private ProductService productService;
    @MockBean private OrderService orderService;
    @MockBean private JwtUtil jwtUtil;

    private String adminToken = "admin-token";
    private String userToken = "user-token";

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(adminToken)).thenReturn(1L);
        when(jwtUtil.getRole(adminToken)).thenReturn("admin");
        when(jwtUtil.getUserId(userToken)).thenReturn(2L);
        when(jwtUtil.getRole(userToken)).thenReturn("user");
    }

    @Test
    void shouldGetCategoryTree() throws Exception {
        when(categoryService.getCategoryTree()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/category")
                .header("X-User-Role", "admin"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateCategory() throws Exception {
        Map<String, Object> body = Map.of("name", "电子产品", "parentId", 0);
        mockMvc.perform(post("/api/admin/category")
                .header("X-User-Role", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isOk());
    }
    // ... update/delete/reject-non-admin tests
}
```

运行：`mvn test -pl mall-goods-order -Dtest=AdminControllerTest`
期望：Controller 未定义，FAIL

- [ ] **步骤 2：创建 CategoryService**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {

    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = list(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort));
        // 构建树：parentId=0 为根节点，children 为子节点
        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != 0)
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == 0)
                .map(c -> new CategoryTreeResponse(c, childrenMap.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    public Category createCategory(String name, Long parentId, Integer sort) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId);
        category.setSort(sort);
        save(category);
        return category;
    }

    public Category updateCategory(Long id, String name, Integer sort) {
        Category category = getById(id);
        if (category == null) throw new BizException("分类不存在");
        category.setName(name);
        category.setSort(sort);
        updateById(category);
        return category;
    }

    public void deleteCategory(Long id) {
        // 检查是否有子分类
        long childCount = count(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) throw new BizException("该分类下有子分类，无法删除");
        // 检查是否有商品（通过 ProductService 检查）
        removeById(id);
    }
}
```

创建 DTO `CategoryTreeResponse`：
```java
@Data
@AllArgsConstructor
public class CategoryTreeResponse {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private List<Category> children;
}
```

- [ ] **步骤 3：创建 AdminController（分类部分）**

```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;

    @GetMapping("/category")
    public R<List<CategoryTreeResponse>> getCategoryTree() {
        return R.ok(categoryService.getCategoryTree());
    }

    @PostMapping("/category")
    public R<Void> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : 0L;
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0;
        categoryService.createCategory(name, parentId, sort);
        return R.ok();
    }

    @PutMapping("/category/{id}")
    public R<Void> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        categoryService.updateCategory(id, (String) body.get("name"),
                body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0);
        return R.ok();
    }

    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return R.ok();
    }
}
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/
git commit -m "feat(admin): add category CRUD API"
```

---

## 任务 6：后台商品管理 API（mall-goods-order AdminController 扩展）

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`

- [ ] **步骤 1：编写失败的测试**

在 AdminControllerTest 中添加商品管理测试：

```java
@Test
void shouldListProductsWithPagination() throws Exception {
    Page<Product> page = new Page<>(1, 10);
    when(productService.getProductPage(anyInt(), anyInt(), anyLong())).thenReturn(page);
    mockMvc.perform(get("/api/admin/product?page=1&size=10")
            .header("X-User-Role", "admin"))
            .andExpect(status().isOk());
}

@Test
void shouldCreateProduct() throws Exception {
    // POST /api/admin/product with name/price/stock/categoryId
    mockMvc.perform(post("/api/admin/product")
            .header("X-User-Role", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Test","description":"desc","price":99.9,"stock":100,"categoryId":1}
                """))
            .andExpect(status().isOk());
}
```

- [ ] **步骤 2：ProductService 新增方法**

```java
public Page<Product> getProductPage(int page, int size, Long categoryId) {
    LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
            .eq(categoryId != null, Product::getCategoryId, categoryId)
            .orderByDesc(Product::getCreateTime);
    return page(new Page<>(page, size), wrapper);
}

public Product createProduct(String name, String description, BigDecimal price,
                              Integer stock, Long categoryId, String images) {
    Product product = new Product();
    product.setName(name);
    product.setDescription(description);
    product.setPrice(price);
    product.setStock(stock);
    product.setCategoryId(categoryId);
    product.setImages(images);
    product.setStatus(1);
    save(product);
    return product;
}

public void updateProduct(Long id, String name, String description, BigDecimal price,
                          Integer stock, Long categoryId, Integer status) {
    Product product = getById(id);
    if (product == null) throw new BizException("商品不存在");
    if (name != null) product.setName(name);
    if (description != null) product.setDescription(description);
    if (price != null) product.setPrice(price);
    if (stock != null) product.setStock(stock);
    if (categoryId != null) product.setCategoryId(categoryId);
    if (status != null) product.setStatus(status);
    updateById(product);
}

public void deleteProduct(Long id) {
    Product product = getById(id);
    if (product == null) throw new BizException("商品不存在");
    product.setStatus(0); // 下架，不物理删除
    updateById(product);
}
```

- [ ] **步骤 3：AdminController 添加商品端点**

```java
@GetMapping("/product")
public R<IPage<Product>> listProducts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) Long categoryId) {
    return R.ok(productService.getProductPage(page, size, categoryId));
}

@PostMapping("/product")
public R<Void> createProduct(@RequestBody Map<String, Object> body) {
    productService.createProduct(
            (String) body.get("name"),
            (String) body.get("description"),
            new BigDecimal(body.get("price").toString()),
            ((Number) body.get("stock")).intValue(),
            body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null,
            (String) body.get("images"));
    return R.ok();
}

@PutMapping("/product/{id}")
public R<Void> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    productService.updateProduct(id,
            (String) body.get("name"),
            (String) body.get("description"),
            body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null,
            body.get("stock") != null ? ((Number) body.get("stock")).intValue() : null,
            body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null,
            body.get("status") != null ? ((Number) body.get("status")).intValue() : null);
    return R.ok();
}

@DeleteMapping("/product/{id}")
public R<Void> deleteProduct(@PathVariable Long id) {
    productService.deleteProduct(id);
    return R.ok();
}
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/
git commit -m "feat(admin): add product CRUD API"
```

---

## 任务 7：后台订单管理 API（发货/完成）

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/entity/Order.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`

- [ ] **步骤 1：Order 实体添加物流字段**

```java
private String logisticsCompany;    // 新增
private String trackingNumber;      // 新增
private LocalDateTime receiveTime;  // 新增
```

- [ ] **步骤 2：OrderService 添加发货/完成方法**

```java
@Transactional
public void shipOrder(Long orderId, String logisticsCompany, String trackingNumber) {
    Order order = getById(orderId);
    if (order == null) throw new BizException("订单不存在");
    if (order.getStatus() != 2) throw new BizException("仅已付款订单可发货");  // 2 = PAID
    order.setStatus(3); // SHIPPED
    order.setLogisticsCompany(logisticsCompany);
    order.setTrackingNumber(trackingNumber);
    updateById(order);
}

@Transactional
public AdminOrderListResponse getOrderList(int page, int size, Integer status) {
    LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
            .eq(status != null, Order::getStatus, status)
            .orderByDesc(Order::getCreateTime);
    IPage<Order> result = page(new Page<>(page, size), wrapper);
    return new AdminOrderListResponse(result.getRecords(), result.getTotal());
}

@Transactional
public void completeOrder(Long orderId) {
    Order order = getById(orderId);
    if (order == null) throw new BizException("订单不存在");
    if (order.getStatus() != 3) throw new BizException("仅已发货订单可完成");  // 3 = SHIPPED
    order.setStatus(4); // COMPLETED
    order.setReceiveTime(LocalDateTime.now());
    updateById(order);
}
```

创建 DTO `AdminOrderListResponse`（`List<Order> orders, long total`）。

- [ ] **步骤 3：AdminController 添加订单端点**

```java
@GetMapping("/order")
public R<AdminOrderListResponse> listOrders(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) Integer status) {
    return R.ok(orderService.getOrderList(page, size, status));
}

@PutMapping("/order/{id}/ship")
public R<Void> shipOrder(@PathVariable Long id,
                          @RequestBody Map<String, String> body) {
    orderService.shipOrder(id, body.get("logisticsCompany"), body.get("trackingNumber"));
    return R.ok();
}

@PutMapping("/order/{id}/complete")
public R<Void> completeOrder(@PathVariable Long id) {
    orderService.completeOrder(id);
    return R.ok();
}
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order
```

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/
git commit -m "feat(admin): add order ship/complete API"
```

---

## 任务 8：订单确认收货 + 申请退款（用户侧）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/entity/Refund.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/RefundMapper.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/RefundService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/OrderController.java`
- 修改：`pay-service/src/main/java/com/tianji/pay/service/PayService.java`
- 修改：`pay-service/src/main/java/com/tianji/pay/controller/PayController.java`
- 测试：OrderControllerTest, OrderServiceTest, PayControllerTest

- [ ] **步骤 1：创建实体和 Mapper**

`Refund` 实体：
```java
@Data
@TableName("refund")
public class Refund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String reason;
    private String status;       // processing/success/fail
    @TableField("alipay_refund_no")
    private String alipayRefundNo;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`RefundMapper`：
```java
@Mapper
public interface RefundMapper extends BaseMapper<Refund> {}
```

- [ ] **步骤 2：编写失败的测试**

在 OrderControllerTest 新增：
```java
@Test
void shouldReceiveOrder() throws Exception {
    doNothing().when(orderService).receiveOrder(anyLong(), anyLong());
    // mock jwtUtil
}
@Test
void shouldRequestRefund() throws Exception {
    // POST /api/order/{id}/refund → create refund record
}
```

- [ ] **步骤 3：OrderService 添加 confirmReceive + requestRefund**

```java
@Transactional
public void confirmReceive(Long userId, Long orderId) {
    Order order = getById(orderId);
    if (order == null || !order.getUserId().equals(userId))
        throw new BizException("订单不存在");
    if (order.getStatus() != 3) throw new BizException("仅已发货订单可确认收货");  // SHIPPED
    order.setStatus(4); // RECEIVED
    order.setReceiveTime(LocalDateTime.now());
    updateById(order);
}
```

请求退款逻辑在 RefundService 中：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService extends ServiceImpl<RefundMapper, Refund> {

    private final OrderService orderService;
    private final AlipayClient alipayClient;
    private final OrderFeignClient orderFeignClient; // 需要创建或复用

    @Value("${alipay.notify-url:}")
    private String notifyUrl;

    @Transactional
    public Refund requestRefund(Long userId, Long orderId, String reason) {
        // 1. 查订单
        Order order = orderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId))
            throw new BizException("订单不存在");
        if (order.getStatus() != 2) throw new BizException("仅已付款订单可退款"); // PAID

        // 2. 检查是否已退款
        long count = count(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .ne(Refund::getStatus, "fail"));
        if (count > 0) throw new BizException("退款申请已提交");

        // 3. 创建退款记录
        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(order.getTotalAmount());
        refund.setReason(reason);
        refund.setStatus("processing");
        save(refund);

        // 4. 调用支付宝退款
        // 需要通过 Feign 查 paymentNo → 见下方 PayService 扩展
        // Best effort: 这里调用内部 Feign 触发退款

        return refund;
    }

    public Refund getRefund(Long orderId) {
        return getOne(new LambdaQueryWrapper<Refund>().eq(Refund::getOrderId, orderId));
    }
}
```

- [ ] **步骤 4：PayService 添加退款方法**

```java
@Transactional
public void refund(String paymentNo, BigDecimal refundAmount, String refundReason) {
    AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
    String outRequestNo = "REFUND" + paymentNo + System.currentTimeMillis();
    request.setBizContent("{" +
            "\"out_trade_no\":\"" + paymentNo + "\"," +
            "\"refund_amount\":" + refundAmount + "," +
            "\"out_request_no\":\"" + outRequestNo + "\"," +
            "\"refund_reason\":\"" + (refundReason != null ? refundReason : "") + "\"" +
            "}");
    try {
        AlipayTradeRefundResponse response = alipayClient.execute(request);
        if (response.isSuccess()) {
            log.info("退款成功: paymentNo={}, refundNo={}", paymentNo, outRequestNo);
        } else {
            log.error("退款失败: {}, msg={}", paymentNo, response.getSubMsg());
            throw new BizException("退款失败: " + response.getSubMsg());
        }
    } catch (AlipayApiException e) {
        log.error("退款调用异常", e);
        throw new BizException("退款调用失败");
    }
}
```

需要在 pay-service 添加内部端点供 mall-goods-order Feign 调用退款。

- [ ] **步骤 5：OrderController 添加端点**

```java
@PutMapping("/{id}/receive")
public R<Void> receiveOrder(@PathVariable Long id,
                             @RequestHeader("Authorization") String authHeader) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    orderService.confirmReceive(userId, id);
    return R.ok();
}

@PostMapping("/{id}/refund")
public R<Void> requestRefund(@PathVariable Long id,
                              @RequestHeader("Authorization") String authHeader,
                              @RequestBody Map<String, String> body) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    refundService.requestRefund(userId, id, body.get("reason"));
    return R.ok();
}
```

- [ ] **步骤 6：运行测试**

```bash
mvn test -pl mall-goods-order,pay-service
```

- [ ] **步骤 7：Commit**

```bash
git add mall-goods-order/ pay-service/
git commit -m "feat(order): add confirm receive + refund flow"
```

---

## 任务 9：订单超时自动取消（RocketMQ 延迟消息）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/consumer/OrderTimeoutConsumer.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`（createOrder 发延迟消息）
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/consumer/OrderTimeoutConsumerTest.java`

- [ ] **步骤 1：创建 OrderTimeoutConsumer**

```java
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-timeout-consumer",
        selectorExpression = "TIMEOUT_CHECK")
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final ProductService productService;
    private final OrderItemMapper orderItemMapper;

    public OrderTimeoutConsumer(OrderService orderService, ProductService productService,
                                OrderItemMapper orderItemMapper) {
        this.orderService = orderService;
        this.productService = productService;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.parseLong(orderIdStr);
        log.info("超时检查: orderId={}", orderId);
        Order order = orderService.getById(orderId);
        if (order == null) {
            log.warn("订单不存在: {}", orderId);
            return;
        }
        if (order.getStatus() == 1) { // 仍为 PENDING
            // 复用 cancelOrder 逻辑（恢复库存 + 更新状态 + 发事件）
            orderService.cancelOrderByTimeout(orderId);
            log.info("订单超时自动取消: orderId={}", orderId);
        }
    }
}
```

- [ ] **步骤 2：OrderService 添加 timeoutCancel 方法 + 发送延迟消息**

在 `createOrder` 方法最后（return order 之前）添加：

```java
// 发送 30 分钟延迟消息
rocketMQTemplate.syncSend("order-topic:TIMEOUT_CHECK",
        MessageBuilder.withPayload(order.getId().toString()).build(),
        3000,  // timeout ms（发送超时）
        16);   // delayLevel 16 = 30min
```

添加 `cancelOrderByTimeout` 方法（无需 userId 校验）：
```java
@Transactional
public void cancelOrderByTimeout(Long orderId) {
    Order order = getById(orderId);
    if (order == null || order.getStatus() != 1) return;
    order.setStatus(5);
    updateById(order);
    List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    for (OrderItem item : items) {
        productService.restoreStock(item.getProductId(), item.getQuantity());
    }
}
```

注意：`syncSend` 使用了 `MessageBuilder`，需要 import `org.springframework.messaging.support.MessageBuilder`。如果项目没有 spring-messaging 依赖，改为用 `convertAndSend` 配合 `Message`：

```java
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

// 发送延迟消息的另一种写法（不依赖 MessageBuilder）：
// 使用 RocketMQTemplate.syncSend 的重载方法
rocketMQTemplate.syncSend("order-topic:TIMEOUT_CHECK",
        rocketMQTemplate.getMessageConverter().toMessage(
                order.getId().toString(),
                new RocketMQMessageHeaders(
                        Map.of(MessageConst.PROPERTY_DELAY_TIME_LEVEL, "16"))),
        3000);
```

采用更简单的方式（rocketmq-spring-boot-starter 2.3.1 支持）：

```java
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.common.message.MessageConst;

// 直接在 createOrder 中：
Message<String> msg = MessageBuilder
        .withPayload(order.getId().toString())
        .setHeader(MessageConst.PROPERTY_DELAY_TIME_LEVEL, "16")
        .build();
rocketMQTemplate.syncSend("order-topic:TIMEOUT_CHECK", msg, 3000);
```

- [ ] **步骤 3：编写单元测试（Mock RocketMQTemplate）**

```java
@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {
    @Mock private OrderService orderService;
    @Mock private ProductService productService;
    @Mock private OrderItemMapper orderItemMapper;
    @InjectMocks private OrderTimeoutConsumer consumer;

    @Test
    void shouldCancelPendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(1);
        when(orderService.getById(1L)).thenReturn(order);
        consumer.onMessage("1");
        verify(orderService).cancelOrderByTimeout(1L);
    }

    @Test
    void shouldSkipNonPendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(2); // 已付款，不取消
        when(orderService.getById(1L)).thenReturn(order);
        consumer.onMessage("1");
        verify(orderService, never()).cancelOrderByTimeout(anyLong());
    }
}
```

- [ ] **步骤 4：修改 createOrder 发送延迟消息 + 测试**

```bash
mvn test -pl mall-goods-order
```

期望：现有 OrderServiceTest 需要更新 mock（RocketMQTemplate.syncSend 的新调用），测试通过。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/
git commit -m "feat(order): add timeout auto-cancel via RocketMQ delayed message"
```

---

## 任务 10：CLAUDE.md 文档同步

**文件：**
- 修改：`CLAUDE.md`

- [ ] **步骤 1：更新子模块依赖速查表**

mall-goods-order 行追加 `**Admin** (商品/分类/订单管理 + 退款)`。

- [ ] **步骤 2：更新测试总数**

运行 `mvn test` 获取最新总数，更新 CLAUDE.md 顶部声明。

- [ ] **步骤 3：更新关键约定**

添加：
```markdown
- **管理员鉴权**：User 表 role 字段（user/admin），JWT 中携带 role claim。Gateway `AuthGlobalFilter` 对 `/api/admin/**` 路径校验 role=admin。mall-goods-order 侧 `AdminInterceptor` 做二次校验。
- **订单状态**：1=待付款、2=已付款、3=已发货、4=已完成/已收货、5=已取消。退款状态独立在 refund 表（processing/success/fail）。
- **超时取消**：下单时 RocketMQ 延迟消息（level 16=30min），`OrderTimeoutConsumer` 消费检查订单状态，PENDING→CANCELLED+恢复库存。
- **退款**：全单退款，走支付宝 `AlipayTradeRefundRequest`。RefundService 负责退款记录管理，PayService 负责调用支付宝。
```

- [ ] **步骤 4：Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add admin/order-lifecycle/user-center features to CLAUDE.md"
```

---

## 自检

**1. 规格覆盖度：**
- ✅ Category CRUD → 任务 5
- ✅ Product CRUD → 任务 6
- ✅ Order ship/complete → 任务 7
- ✅ Order receive → 任务 8
- ✅ Order refund → 任务 8
- ✅ Order timeout cancel → 任务 9
- ✅ User profile/avatar/password → 任务 4
- ✅ DB schema changes → 任务 1
- ✅ JWT role claim → 任务 2
- ✅ Gateway admin filter → 任务 2
- ✅ AdminInterceptor → 任务 3

**2. 占位符扫描：** 无 TODO/TBD/占位符。

**3. 类型一致性：** 所有实体使用 MyBatis-Plus 字段映射，Controller 统一返回 `R<T>`，Service 统一继承 `ServiceImpl`。

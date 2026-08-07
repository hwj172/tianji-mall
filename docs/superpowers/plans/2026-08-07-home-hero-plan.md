# 首页首屏打磨 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 升级首页首屏 hero 区视觉——分类侧栏加 emoji 图标、Banner 渐变叠加 + 指示器美化、右侧改「渐变用户卡 + 快捷入口 4 宫格 + 公告」。

**架构：** 纯前端 scoped CSS 改造，改动 `HomeBanner.vue`（渐变层 + 指示器）与 `HomePage.vue`（分类侧栏、右侧 hero）。后端零改动，分类图标用前端名称关键词 → emoji 映射，不新增字段。

**技术栈：** Vue3 `<script setup>` + Element Plus（el-carousel、el-button、el-icon）+ scoped CSS。

**参考规格：** `docs/superpowers/specs/2026-08-07-home-hero-design.md`

---

## 文件结构

- 修改：`tianji-mall-frontend/src/components/home/HomeBanner.vue` — banner 渐变叠加层 + 轮播指示器自定义
- 修改：`tianji-mall-frontend/src/views/home/HomePage.vue` — 分类侧栏升级 + 右侧用户卡/快捷入口/公告
- 测试/验证：`tianji-mall-frontend` 执行 `npm run build`；浏览器 `http://localhost:5173` 视觉确认

---

### 任务 1：HomeBanner.vue 渐变叠加 + 指示器自定义

**文件：**
- 修改：`tianji-mall-frontend/src/components/home/HomeBanner.vue`

- [ ] **步骤 1：替换 HomeBanner.vue 为以下完整内容**

```vue
<template>
  <div class="home-banner" v-if="banners.length">
    <el-carousel :interval="4000" arrow="hover" height="300px">
      <el-carousel-item v-for="banner in banners" :key="banner.id">
        <a :href="banner.linkUrl || '#'" class="banner-link">
          <img :src="banner.imageUrl" :alt="banner.title" @error="onImageError" />
        </a>
      </el-carousel-item>
    </el-carousel>
  </div>
  <div class="home-banner placeholder" v-else>
    <div class="placeholder-content">
      <h3>天机商城</h3>
      <p>仿淘宝智能电商平台</p>
    </div>
  </div>
</template>

<script setup>
defineProps({ banners: { type: Array, default: () => [] } })

function onImageError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.home-banner { border-radius: 8px; overflow: hidden; }
.banner-link { position: relative; display: block; height: 300px; }
.banner-link::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: 0; height: 60px;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, .25));
  pointer-events: none;
}
.banner-link img { width: 100%; height: 100%; object-fit: cover; }
.placeholder {
  height: 300px;
  background: linear-gradient(135deg, #ff6b35, #ff5000);
  display: flex; align-items: center; justify-content: center;
}
.placeholder-content { text-align: center; color: #fff; }
.placeholder-content h3 { font-size: 32px; margin-bottom: 8px; }
.placeholder-content p { font-size: 14px; opacity: .9; }

/* 轮播指示器：普通态小圆点，active 态拉长圆角条 */
.home-banner :deep(.el-carousel__indicators--horizontal) { bottom: 14px; }
.home-banner :deep(.el-carousel__indicator) { padding: 0 3px; }
.home-banner :deep(.el-carousel__indicator .el-carousel__button) {
  width: 7px; height: 7px; border-radius: 50%; background: #fff; opacity: .6;
  transition: width .2s;
}
.home-banner :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 18px; border-radius: 4px; opacity: 1;
}
</style>
```

- [ ] **步骤 2：构建验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：`✓ built in Xs`，无编译错误。dev server（5173）HMR 自动生效。

- [ ] **步骤 3：Commit**

```bash
git add tianji-mall-frontend/src/components/home/HomeBanner.vue
git commit -m "style(frontend): add banner gradient overlay and custom carousel indicators"
```

---

### 任务 2：HomePage.vue 分类侧栏升级

**文件：**
- 修改：`tianji-mall-frontend/src/views/home/HomePage.vue`

- [ ] **步骤 1：替换分类侧栏 template 部分**

将现有 `category-sidebar` 块（第 5-18 行）替换为：

```vue
      <div class="category-sidebar">
        <div class="category-title">全部商品分类</div>
        <div
          v-for="cat in categories.slice(0, 10)"
          :key="cat.id"
          class="category-item"
          :class="{ active: activeCategory === cat.id }"
          @mouseenter="activeCategory = cat.id"
          @mouseleave="activeCategory = null"
          @click="$router.push({ name: 'productList', query: { categoryId: cat.id } })"
        >
          <span>{{ categoryIcon(cat.name) }} {{ cat.name }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
```

- [ ] **步骤 2：script 加 emoji 映射**

在 `<script setup>` 中 `const activeCategory = ref(null)` 之后添加：

```js
const CATEGORY_ICONS = [
  { keyword: '手机', icon: '📱' },
  { keyword: '电脑', icon: '💻' },
  { keyword: '办公', icon: '💻' },
  { keyword: '服饰', icon: '👕' },
  { keyword: '鞋', icon: '👟' },
  { keyword: '智能', icon: '⌚' },
  { keyword: '家电', icon: '🏠' },
  { keyword: '家居', icon: '🛋️' },
  { keyword: '运动', icon: '⚽' },
  { keyword: '美妆', icon: '💄' },
  { keyword: '食品', icon: '🍎' },
  { keyword: '图书', icon: '📚' },
  { keyword: '玩具', icon: '🧸' },
]

function categoryIcon(name) {
  const hit = CATEGORY_ICONS.find(i => name.includes(i.keyword))
  return hit ? hit.icon : '📦'
}
```

- [ ] **步骤 3：替换分类侧栏样式**

将现有 `.category-title` / `.category-item` 相关样式（第 114-116 行）替换为：

```css
.category-title { position: relative; padding: 10px 16px; font-weight: 600; font-size: 14px; color: #ff5000; }
.category-title::after {
  content: '';
  position: absolute; left: 16px; right: 16px; bottom: 0; height: 2px;
  background: linear-gradient(90deg, #ff5000, #ff7a3d); border-radius: 1px;
}
.category-item { padding: 7px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 13px; cursor: pointer; transition: background .15s; }
.category-item:hover, .category-item.active { color: #ff5000; background: #fff5f0; font-weight: 600; }
```

- [ ] **步骤 4：构建验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：`✓ built in Xs`，无错误。

- [ ] **步骤 5：Commit**

```bash
git add tianji-mall-frontend/src/views/home/HomePage.vue
git commit -m "style(frontend): add category icons and hover highlight on home sidebar"
```

---

### 任务 3：HomePage.vue 右侧用户卡 + 快捷入口宫格

**文件：**
- 修改：`tianji-mall-frontend/src/views/home/HomePage.vue`

- [ ] **步骤 1：替换右侧 hero-sidebar template 部分**

将现有 `hero-sidebar` 块（第 20-38 行）替换为：

```vue
      <div class="hero-sidebar">
        <div class="hero-user">
          <div class="user-avatar">👤</div>
          <div class="user-hi">{{ userStore.isLoggedIn ? `Hi, ${userStore.userInfo?.username}` : 'Hi, 欢迎光临' }}</div>
          <el-button v-if="!userStore.isLoggedIn" class="user-login-btn" size="small" round @click="$router.push('/login')">登录 / 注册</el-button>
          <div class="user-tags">
            <template v-if="userStore.isLoggedIn">
              <span @click="$router.push('/order/list')">我的订单</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
            <template v-else>
              <span>新人福利</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
          </div>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/seckill')"><div class="quick-icon">⚡</div><div class="quick-label">限时秒杀</div></div>
          <div class="quick-item" @click="$router.push('/groupbuy')"><div class="quick-icon">🎯</div><div class="quick-label">阶梯拼团</div></div>
          <div class="quick-item" @click="$router.push('/coupon/center')"><div class="quick-icon">🎫</div><div class="quick-label">领券中心</div></div>
          <div class="quick-item" @click="$router.push('/chat')"><div class="quick-icon">🤖</div><div class="quick-label">AI 导购</div></div>
        </div>
        <div class="hero-notice">
          <h4>📢 公告</h4>
          <p>🆕 秒杀专区已上线</p>
          <p>🎉 阶梯拼团新玩法</p>
          <p>🤖 AI 导购帮你挑</p>
        </div>
      </div>
```

- [ ] **步骤 2：替换右侧相关样式**

将现有 `.hero-sidebar` 起至 `.hero-notice p` 的样式（第 118-126 行）替换为：

```css
.hero-sidebar { width: 220px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.hero-user {
  background: linear-gradient(135deg, #ff7a3d, #ff5000);
  border-radius: 10px; padding: 14px; text-align: center; color: #fff;
  box-shadow: 0 2px 8px rgba(255, 80, 0, .2);
}
.user-avatar { font-size: 32px; margin-bottom: 6px; }
.user-hi { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.user-login-btn { border: 1px solid #fff; background: transparent; color: #fff; }
.user-login-btn:hover { background: rgba(255, 255, 255, .15); color: #fff; }
.user-tags { display: flex; gap: 8px; margin-top: 10px; font-size: 11px; justify-content: center; }
.user-tags span { background: rgba(255, 255, 255, .18); color: #fff; padding: 2px 8px; border-radius: 4px; cursor: pointer; }
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.quick-item {
  background: #fff; border-radius: 10px; padding: 10px 0; text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .05); cursor: pointer;
  transition: transform .15s, box-shadow .15s;
}
.quick-item:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, .1); }
.quick-icon { font-size: 20px; }
.quick-label { font-size: 11px; color: #666; margin-top: 2px; }
.hero-notice { background: #fff; border-radius: 8px; padding: 12px; flex: 1; }
.hero-notice h4 { font-size: 13px; margin-bottom: 8px; color: #333; }
.hero-notice p { font-size: 12px; color: #666; padding: 3px 0; }
```

- [ ] **步骤 3：构建验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：`✓ built in Xs`，无错误。

- [ ] **步骤 4：Commit**

```bash
git add tianji-mall-frontend/src/views/home/HomePage.vue
git commit -m "style(frontend): revamp home hero right panel with gradient user card and quick entries"
```

---

### 任务 4：全量验证 + 浏览器视觉确认

**文件：** 无代码改动

- [ ] **步骤 1：前端 build**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：`✓ built in Xs`，`dist/` 生成成功。

- [ ] **步骤 2：浏览器验证（本地 dev 5173）**

打开 `http://localhost:5173`，逐项确认：

1. 分类侧栏：每项显示 emoji 图标 + 名称；hover 橙色高亮 + 加粗；标题下方渐变线
2. Banner：图片底部渐变叠加；轮播指示器为白色圆点，active 拉长
3. 右侧用户卡：橙红渐变背景、白色文字；未登录显示「登录/注册」描边按钮，已登录显示 username + 「我的订单/领券中心」标签
4. 快捷入口 4 宫格：⚡秒杀/🎯拼团/🎫领券/🤖AI，hover 上浮；点击跳转对应路由
5. 公告在右侧底部；hero 三栏整体等高不破版

- [ ] **步骤 3：Commit（如验证中发现小修）**

```bash
git add tianji-mall-frontend/src/views/home/HomePage.vue tianji-mall-frontend/src/components/home/HomeBanner.vue
git commit -m "style(frontend): final polish on home hero"
```

（无改动则跳过本步骤。）

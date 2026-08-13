<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="aside-header"><router-link to="/">← 回商城</router-link></div>
      <el-menu :default-active="route.path" router :ellipsis="false">
        <el-menu-item index="/admin"><el-icon><DataAnalysis /></el-icon> 数据看板</el-menu-item>
        <el-menu-item index="/admin/categories"><el-icon><Grid /></el-icon> 分类管理</el-menu-item>
        <el-menu-item index="/admin/products"><el-icon><Goods /></el-icon> 商品管理</el-menu-item>
        <el-menu-item index="/admin/orders"><el-icon><Document /></el-icon> 订单管理</el-menu-item>
        <el-menu-item index="/admin/coupons"><el-icon><Discount /></el-icon> 优惠券管理</el-menu-item>
        <el-menu-item index="/admin/promotions"><el-icon><Present /></el-icon> 满减活动</el-menu-item>
        <el-menu-item index="/admin/announcement"><el-icon><Bell /></el-icon> 系统公告</el-menu-item>
        <el-menu-item index="/admin/shops"><el-icon><Shop /></el-icon> 店铺管理</el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><User /></el-icon> 用户管理</el-menu-item>
        <el-menu-item index="/admin/banner"><el-icon><Picture /></el-icon> Banner 管理</el-menu-item>
        <el-menu-item index="/admin/skus"><el-icon><CollectionTag /></el-icon> SKU/属性管理</el-menu-item>
        <el-menu-item index="/admin/seckill"><el-icon><AlarmClock /></el-icon> 秒杀管理</el-menu-item>
        <el-menu-item index="/admin/group-buy"><el-icon><Connection /></el-icon> 拼团管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar"><span>管理后台</span></el-header>
      <el-main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.admin-layout { min-height: 100vh; }
.aside { background: #304156; }
.aside-header { padding: 16px; font-size: 14px; }
.aside-header a { color: #fff; }
.aside-header a:hover { color: #ff7a3d; }
/* 深色侧栏：el-menu 默认白底 → 透明 + 浅灰文字 + 橙色高亮 */
.aside :deep(.el-menu) { background: transparent; border-right: none; }
.aside :deep(.el-menu-item) { color: #bfcbd9; }
.aside :deep(.el-menu-item:hover) { background: rgba(255,255,255,.08); color: #fff; }
.aside :deep(.el-menu-item.is-active) { background: #ff5000; color: #fff; }
.topbar { background: #161e31; display: flex; align-items: center; padding: 0 20px; font-size: 16px; border-bottom: 1px solid rgba(255, 80, 0, .4); }
.content { background: transparent; min-height: calc(100vh - 60px); padding: 20px; }
</style>

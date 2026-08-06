<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="aside-header"><router-link to="/" style="color:#ff5000">← 回商城</router-link></div>
      <el-menu :default-active="route.path" router :ellipsis="false">
        <el-menu-item index="/admin"><el-icon><DataAnalysis /></el-icon> 数据看板</el-menu-item>
        <el-menu-item index="/admin/categories"><el-icon><Grid /></el-icon> 分类管理</el-menu-item>
        <el-menu-item index="/admin/products"><el-icon><Goods /></el-icon> 商品管理</el-menu-item>
        <el-menu-item index="/admin/orders"><el-icon><Document /></el-icon> 订单管理</el-menu-item>
        <el-menu-item index="/admin/coupons"><el-icon><Discount /></el-icon> 优惠券管理</el-menu-item>
        <el-menu-item index="/admin/shops"><el-icon><Shop /></el-icon> 店铺管理</el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><User /></el-icon> 用户管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar"><span>管理后台</span></el-header>
      <el-main class="content"><router-view /></el-main>
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
.topbar { background: #fff; display: flex; align-items: center; padding: 0 20px; font-size: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.content { background: #f0f2f5; min-height: calc(100vh - 60px); padding: 20px; }
</style>

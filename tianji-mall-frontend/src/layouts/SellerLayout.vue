<template>
  <el-container class="seller-layout">
    <el-aside width="220px" class="aside">
      <div class="aside-header"><router-link to="/" style="color:#ff5000">← 回商城</router-link></div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/seller"><el-icon><DataAnalysis /></el-icon> 商家看板</el-menu-item>
        <el-menu-item index="/seller/products"><el-icon><Goods /></el-icon> 商品管理</el-menu-item>
        <el-menu-item index="/seller/orders"><el-icon><Document /></el-icon> 订单管理</el-menu-item>
        <el-menu-item index="/seller/reviews"><el-icon><ChatDotRound /></el-icon> 商品评价</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar"><span>商家中心</span></el-header>
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
.seller-layout { min-height: 100vh; }
.aside { background: #304156; }
.aside-header { padding: 16px; font-size: 14px; }
.topbar { background: #fff; display: flex; align-items: center; padding: 0 20px; font-size: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.content { background: #f0f2f5; min-height: calc(100vh - 60px); padding: 20px; }
</style>

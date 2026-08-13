<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script setup></script>
<style scoped>
.not-found { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 60vh; }
.not-found h1 { font-size: 80px; color: #dcdfe6; }
.not-found p { font-size: 18px; color: #52525b; margin: 16px 0; }
</style>

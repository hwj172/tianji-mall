<template>
  <div class="following-page" v-loading="loading">
    <h2>我关注的店铺</h2>

    <div class="shop-grid" v-if="shops.length">
      <div class="shop-card" v-for="s in shops" :key="s.id" @click="$router.push(`/shop/${s.id}`)">
        <el-avatar v-if="s.logo" :size="56" shape="square" :src="s.logo" />
        <el-avatar v-else :size="56" shape="square" :icon="Shop" />
        <div class="sc-info">
          <h3>{{ s.name }}</h3>
          <p>{{ s.description || '暂无店铺描述' }}</p>
        </div>
        <span class="sc-enter">进入店铺 →</span>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="还没有关注的店铺">
      <el-button type="primary" plain @click="$router.push('/')">去逛逛</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Shop } from '@element-plus/icons-vue'
import { getFollowingShops } from '@/api'

const shops = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getFollowingShops()
    shops.value = res.data || []
  } catch { /* handle by interceptor */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.following-page { max-width: 900px; margin: 0 auto; }
.following-page h2 { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
.shop-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.shop-card { background: #1b1b1e; border-radius: 8px; padding: 16px; display: flex; align-items: center; gap: 14px; cursor: pointer; transition: box-shadow .2s; }
.shop-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.sc-info { flex: 1; min-width: 0; }
.sc-info h3 { font-size: 15px; margin-bottom: 4px; }
.sc-info p { font-size: 12px; color: #52525b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sc-enter { font-size: 13px; color: #ff5000; white-space: nowrap; }
</style>

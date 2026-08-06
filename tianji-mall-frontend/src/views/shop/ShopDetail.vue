<template>
  <div class="shop-page" v-if="shop">
    <!-- 店铺头部 -->
    <div class="shop-header">
      <div class="sh-left">
        <el-avatar v-if="shop.logo" :src="shop.logo" :size="72" shape="square" />
        <el-avatar v-else :size="72" shape="square" :icon="Shop" />
        <div class="sh-info">
          <h2>{{ shop.name }}</h2>
          <p>{{ shop.description || '暂无店铺描述' }}</p>
          <div class="sh-stats">
            <span>粉丝 {{ followerCount }}</span>
          </div>
        </div>
      </div>
      <div class="sh-actions">
        <el-button
          :type="isFollowing ? 'default' : 'danger'"
          @click="handleFollow"
          :loading="following"
        >{{ isFollowing ? '已关注' : '+ 关注' }}</el-button>
      </div>
    </div>

    <!-- 商品列表 -->
    <div class="shop-products">
      <h3>全部商品</h3>
      <div class="product-grid" v-if="products.length">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
      <el-empty v-else description="暂无商品" />

      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
          background
        />
      </div>
    </div>
  </div>

  <el-empty v-else-if="!loading" description="店铺不存在或已关闭" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getShopDetail, followShop } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'

const route = useRoute()
const shop = ref(null)
const products = ref([])
const total = ref(0)
const followerCount = ref(0)
const isFollowing = ref(false)
const loading = ref(false)
const following = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getShopDetail(route.params.id, { page: currentPage.value, size: pageSize.value })
    if (res.data) {
      shop.value = res.data.shop
      products.value = res.data.products?.records || []
      total.value = res.data.products?.total || 0
      followerCount.value = res.data.followerCount || 0
      isFollowing.value = res.data.isFollowing || false
    }
  } catch (e) {
    console.error('加载店铺详情失败', e)
  }
  finally { loading.value = false }
}

async function handleFollow() {
  following.value = true
  try {
    const res = await followShop(shop.value.id)
    isFollowing.value = !isFollowing.value
    followerCount.value += isFollowing.value ? 1 : -1
    ElMessage.success(isFollowing.value ? '已关注' : '已取消关注')
  } catch { /* handle by interceptor */ }
  finally { following.value = false }
}
</script>

<style scoped>
.shop-page { max-width: 1200px; margin: 0 auto; }

.shop-header { background: #fff; border-radius: 8px; padding: 24px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.sh-left { display: flex; align-items: center; gap: 16px; }
.sh-info h2 { font-size: 22px; margin-bottom: 6px; }
.sh-info p { font-size: 13px; color: #666; margin-bottom: 8px; }
.sh-stats { font-size: 13px; color: #999; }

.shop-products h3 { font-size: 17px; margin-bottom: 14px; }
.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>

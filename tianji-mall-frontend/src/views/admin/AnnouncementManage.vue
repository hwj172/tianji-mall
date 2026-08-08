<template>
  <div class="admin-page">
    <div class="ap-header"><h2>系统公告</h2></div>
    <el-alert type="info" :closable="false" title="发布后对所有用户的消息中心广播（类型：系统公告）" style="margin-bottom:16px" />

    <el-form :model="form" label-width="70px" style="max-width: 520px">
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="公告标题" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="form.content" type="textarea" :rows="4" maxlength="512" show-word-limit placeholder="公告内容" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handlePublish" :loading="saving">发布公告</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAnnouncement } from '@/api'

const form = reactive({ title: '', content: '' })
const saving = ref(false)

async function handlePublish() {
  if (!form.title.trim()) { ElMessage.warning('请输入公告标题'); return }
  if (!form.content.trim()) { ElMessage.warning('请输入公告内容'); return }
  saving.value = true
  try {
    await createAnnouncement({ title: form.title.trim(), content: form.content.trim() })
    ElMessage.success('公告已发布')
    form.title = ''
    form.content = ''
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}
</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>

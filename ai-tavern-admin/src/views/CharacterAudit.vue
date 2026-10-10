<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveCharacter,
  getAdminCharacterDetail,
  getAuditCharacters,
  offlineCharacter,
  rejectCharacter,
} from '../api/admin'

const loading = ref(false)
const detailLoading = ref(false)
const tableData = ref([])
const pagination = ref({ page: 1, pageSize: 10, total: 0 })
const activeTab = ref('pending')

const drawerVisible = ref(false)
const current = ref(null)

const statusLabel = {
  0: '草稿',
  1: '待审核',
  2: '已上架',
  3: '已下架',
  4: '已驳回',
  pending: '待审核',
}

const statusTagType = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'info',
  4: 'danger',
}

function normalizeList(data) {
  if (Array.isArray(data)) return { list: data, total: data.length }
  return {
    list: data?.list || data?.items || [],
    total: data?.total ?? data?.list?.length ?? 0,
  }
}

function formatStatus(status) {
  return statusLabel[status] || String(status ?? '-')
}

async function loadData() {
  loading.value = true
  try {
    const data = await getAuditCharacters({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      status: activeTab.value,
    })
    const { list, total } = normalizeList(data)
    tableData.value = list
    pagination.value.total = total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  drawerVisible.value = true
  detailLoading.value = true
  current.value = row
  try {
    current.value = await getAdminCharacterDetail(row.id)
  } catch {
    // 列表字段已足够时仍展示
  } finally {
    detailLoading.value = false
  }
}

const tagNames = computed(() => {
  const tags = current.value?.tags
  if (!Array.isArray(tags)) return []
  return tags.map((t) => (typeof t === 'string' ? t : t.name)).filter(Boolean)
})

const canApprove = computed(() => {
  const s = current.value?.status
  return s === 1 || s === 'pending' || s === 4
})

const canReject = computed(() => {
  const s = current.value?.status
  return s === 1 || s === 'pending' || s === 2
})

const canOffline = computed(() => {
  const s = current.value?.status
  return s === 2 || s === 'approved' || s === 'online'
})

async function handleApprove(row) {
  const target = row || current.value
  await ElMessageBox.confirm(`确认通过角色「${target.name}」并上架？`, '审核通过')
  await approveCharacter(target.id)
  ElMessage.success('已通过')
  drawerVisible.value = false
  loadData()
}

async function handleReject(row) {
  const target = row || current.value
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回角色', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputPlaceholder: '驳回原因',
    inputValidator: (val) => !!val?.trim() || '请输入驳回原因',
  })
  await rejectCharacter(target.id, { reason: value })
  ElMessage.success('已驳回')
  drawerVisible.value = false
  loadData()
}

async function handleOffline(row) {
  const target = row || current.value
  await ElMessageBox.confirm(`确认下架角色「${target.name}」？`, '下架角色', { type: 'warning' })
  await offlineCharacter(target.id)
  ElMessage.success('已下架')
  drawerVisible.value = false
  loadData()
}

function handlePageChange(page) {
  pagination.value.page = page
  loadData()
}

watch(activeTab, () => {
  pagination.value.page = 1
  loadData()
})

onMounted(loadData)
</script>

<template>
  <el-card shadow="hover">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审核" name="pending" />
      <el-tab-pane label="已上架" name="online" />
      <el-tab-pane label="已下架" name="offline" />
      <el-tab-pane label="已驳回" name="rejected" />
    </el-tabs>

    <el-table v-loading="loading" :data="tableData" stripe @row-click="openDetail">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <div class="name-cell">
            <el-avatar :size="36" :src="row.avatar" shape="square">{{ row.name?.[0] }}</el-avatar>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建者" min-width="110">
        <template #default="{ row }">
          {{ row.creatorName || row.creator || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="简介" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.status] || 'info'" size="small">
            {{ formatStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="160">
        <template #default="{ row }">
          {{ row.updatedAt || row.createdAt || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click.stop="openDetail(row)">查看详情</el-button>
          <el-button
            v-if="row.status === 1 || row.status === 4"
            type="success"
            link
            @click.stop="handleApprove(row)"
          >
            通过
          </el-button>
          <el-button
            v-if="row.status === 1 || row.status === 2"
            type="danger"
            link
            @click.stop="handleReject(row)"
          >
            驳回
          </el-button>
          <el-button
            v-if="row.status === 2"
            type="warning"
            link
            @click.stop="handleOffline(row)"
          >
            下架
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </el-card>

  <el-drawer
    v-model="drawerVisible"
    size="560px"
    :title="current ? `审核：${current.name}` : '角色详情'"
    destroy-on-close
  >
    <div v-loading="detailLoading" class="detail">
      <template v-if="current">
        <div class="hero">
          <el-avatar :size="72" :src="current.avatar" shape="square">
            {{ current.name?.[0] }}
          </el-avatar>
          <div>
            <h2>{{ current.name }}</h2>
            <div class="meta">
              <el-tag :type="statusTagType[current.status] || 'info'" size="small">
                {{ formatStatus(current.status) }}
              </el-tag>
              <span>创建者：{{ current.creatorName || current.creator || '-' }}</span>
              <span>ID：{{ current.id }}</span>
            </div>
          </div>
        </div>

        <el-descriptions :column="1" border size="small" class="block">
          <el-descriptions-item label="分类">
            {{ current.categoryName || current.categoryId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag v-for="t in tagNames" :key="t" size="small" class="tag">{{ t }}</el-tag>
            <span v-if="!tagNames.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="采样参数">
            temp={{ current.temperature ?? '-' }} /
            top_p={{ current.topP ?? '-' }} /
            max_tokens={{ current.maxTokens ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="block">
          <h3>简介</h3>
          <pre class="text">{{ current.description || '（无）' }}</pre>
        </section>
        <section class="block">
          <h3>性格人设</h3>
          <pre class="text">{{ current.personality || '（无）' }}</pre>
        </section>
        <section class="block">
          <h3>背景故事</h3>
          <pre class="text">{{ current.backgroundStory || '（无）' }}</pre>
        </section>
        <section class="block">
          <h3>开场白</h3>
          <pre class="text">{{ current.greeting || '（无）' }}</pre>
        </section>
        <section class="block">
          <h3>示例对话</h3>
          <pre class="text">{{ current.exampleDialogue || '（无）' }}</pre>
        </section>

        <div class="actions">
          <el-button v-if="canApprove" type="success" @click="handleApprove()">通过上架</el-button>
          <el-button v-if="canReject" type="danger" @click="handleReject()">驳回</el-button>
          <el-button v-if="canOffline" type="warning" @click="handleOffline()">下架</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail {
  min-height: 200px;
  padding-bottom: 24px;
}

.hero {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
}

.hero h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #606266;
  font-size: 13px;
}

.block {
  margin-top: 16px;
}

.block h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

.text {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  max-height: 280px;
  overflow: auto;
  color: #303133;
}

.tag {
  margin-right: 6px;
}

.actions {
  position: sticky;
  bottom: 0;
  display: flex;
  gap: 8px;
  margin-top: 20px;
  padding-top: 12px;
  background: #fff;
}
</style>

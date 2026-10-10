<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { banUser, getUsers, updateMembership } from '../api/admin'

const loading = ref(false)
const tableData = ref([])
const pagination = ref({ page: 1, pageSize: 10, total: 0 })
const keyword = ref('')

const membershipOptions = [
  { label: '普通', value: 'free' },
  { label: 'VIP', value: 'vip' },
  { label: 'SVIP', value: 'svip' },
]

function normalizeList(data) {
  if (Array.isArray(data)) return { list: data, total: data.length }
  return {
    list: data?.list || data?.items || [],
    total: data?.total ?? data?.list?.length ?? 0,
  }
}

async function loadData() {
  loading.value = true
  try {
    const data = await getUsers({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      keyword: keyword.value || undefined,
    })
    const { list, total } = normalizeList(data)
    tableData.value = list
    pagination.value.total = total
  } finally {
    loading.value = false
  }
}

async function handleBan(row) {
  const isBanned = row.banned || row.status === 'banned'
  const action = isBanned ? '解封' : '封禁'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`, `${action}用户`, {
    type: 'warning',
  })
  await banUser(row.id, { banned: !isBanned })
  ElMessage.success(`已${action}`)
  loadData()
}

async function handleMembership(row) {
  const { value } = await ElMessageBox.prompt('选择会员等级', '修改会员等级', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputValue: row.membership || row.membershipLevel || 'free',
    inputPlaceholder: 'free / vip / svip',
    inputValidator: (val) => !!val?.trim() || '请输入会员等级',
  })
  await updateMembership(row.id, { level: value, membership: value })
  ElMessage.success('会员等级已更新')
  loadData()
}

function handleSearch() {
  pagination.value.page = 1
  loadData()
}

function handlePageChange(page) {
  pagination.value.page = page
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="hover">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名/邮箱"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="membership" label="会员等级" width="120">
        <template #default="{ row }">
          <el-tag>{{ row.membership || row.membershipLevel || 'free' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.banned || row.status === 'banned' ? 'danger' : 'success'">
            {{ row.banned || row.status === 'banned' ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" min-width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            :type="row.banned || row.status === 'banned' ? 'success' : 'danger'"
            link
            @click="handleBan(row)"
          >
            {{ row.banned || row.status === 'banned' ? '解封' : '封禁' }}
          </el-button>
          <el-button type="primary" link @click="handleMembership(row)">会员等级</el-button>
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
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>

<script setup>
import { onMounted, ref } from 'vue'
import { getViolations } from '../api/admin'

const loading = ref(false)
const tableData = ref([])
const pagination = ref({ page: 1, pageSize: 10, total: 0 })
const severityFilter = ref('')

function normalizeList(data) {
  if (Array.isArray(data)) return { list: data, total: data.length }
  return {
    list: data?.list || data?.items || [],
    total: data?.total ?? data?.list?.length ?? 0,
  }
}

function severityType(severity) {
  const map = { high: 'danger', medium: 'warning', low: 'info' }
  return map[severity] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await getViolations({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      severity: severityFilter.value || undefined,
    })
    const { list, total } = normalizeList(data)
    tableData.value = list
    pagination.value.total = total
  } finally {
    loading.value = false
  }
}

function handleFilter() {
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
      <el-select v-model="severityFilter" placeholder="严重程度" clearable style="width: 160px">
        <el-option label="高" value="high" />
        <el-option label="中" value="medium" />
        <el-option label="低" value="low" />
      </el-select>
      <el-button type="primary" @click="handleFilter">筛选</el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="type" label="违规类型" min-width="120" />
      <el-table-column prop="content" label="违规内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="severity" label="严重程度" width="100">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)">{{ row.severity || 'unknown' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="记录时间" min-width="160" />
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

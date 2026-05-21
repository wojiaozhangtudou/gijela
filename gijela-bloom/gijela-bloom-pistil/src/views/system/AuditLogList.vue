<template>
  <div>
    <el-card>
      <div style="display:flex;justify-content:space-between;margin-bottom:12px">
  <el-input v-model="filters.keyword" :placeholder="$t('message.keyword')" style="width:240px" />
        <div>
          <el-button type="primary" @click="onSearch">{{ $t('message.search') }}</el-button>
          <el-button @click="onReset">{{ $t('message.reset') }}</el-button>
        </div>
      </div>

      <el-table :data="list" style="width:100%" :loading="tableLoading">
  <el-table-column prop="id" :label="$t('message.id')" width="80" />
  <el-table-column prop="operator" :label="$t('message.operator')" />
  <el-table-column prop="action" :label="$t('message.action')" />
  <el-table-column prop="timestamp" :label="$t('message.time')" />
      </el-table>

      <div style="margin-top:12px;text-align:right">
        <el-pagination
          background
          layout="prev, pager, next, sizes, total"
          :total="total"
          :page-size="page.size"
          :current-page="page.current"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { defineComponent, reactive, ref, onMounted } from 'vue'
import { pageAuditLogs } from '../../api/audit'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

export default defineComponent({
  setup() {
  const { t } = useI18n()
    const filters = reactive({ keyword: '' })
    const page = reactive({ current: 1, size: 20 })
    const total = ref(0)
    const list = ref<any[]>([])
    const tableLoading = ref(false)

    async function load() {
      tableLoading.value = true
      try {
        const r = await pageAuditLogs({ current: page.current, size: page.size, q: filters.keyword })
        const d = r?.data || { records: [], total: 0 }
        list.value = d.records || d || []
        total.value = d.total || (Array.isArray(d) ? d.length : 0)
  } catch (e) { ElMessage.error(String(t('message.load_audit_failed'))) } finally { tableLoading.value = false }
    }

    function onSearch() { page.current = 1; load() }
    function onReset() { filters.keyword = ''; onSearch() }
    function onPageChange(p: number) { page.current = p; load() }
    function onSizeChange(s: number) { page.size = s; load() }

    onMounted(() => { load() })
    return { filters, page, total, list, tableLoading, onSearch, onReset, onPageChange, onSizeChange }
  }
})
</script>

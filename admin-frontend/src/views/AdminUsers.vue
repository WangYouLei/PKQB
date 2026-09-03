<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">账号管理</h2>
      <button class="btn btn-primary" @click="openCreate">+ 新增账号</button>
    </div>

    <div class="toolbar">
      <input v-model="query.keyword" class="form-input" placeholder="用户名 / 学号" @keyup.enter="search" />
      <select v-model="query.classId" class="form-select">
        <option :value="undefined">全部班级</option>
        <option v-for="c in classes" :key="c.id" :value="c.id">{{ c.className }}</option>
      </select>
      <select v-model="query.role" class="form-select">
        <option :value="undefined">全部角色</option>
        <option :value="0">普通用户</option>
        <option :value="1">管理员</option>
      </select>
      <button class="btn" @click="search">搜索</button>
      <button class="btn" @click="resetSearch">重置</button>
    </div>

    <div class="table-card">
      <table>
        <thead>
          <tr>
            <th>ID</th><th>用户名</th><th>学号</th><th>班级</th><th>角色</th><th>创建时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.username }}</td>
            <td>{{ row.studentNo }}</td>
            <td>{{ row.className || '-' }}</td>
            <td><span class="tag" :class="row.role === 1 ? 'tag-admin' : 'tag-user'">{{ row.role === 1 ? '管理员' : '普通' }}</span></td>
            <td>{{ row.createTime }}</td>
            <td>
              <button class="btn btn-sm" @click="openEdit(row)">编辑</button>
              <button class="btn btn-sm" @click="openReset(row)">重置密码</button>
              <button class="btn btn-sm btn-danger" @click="remove(row)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="7" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>

    <div class="pagination">
      <span>共 {{ total }} 条，第 {{ query.page }} / {{ totalPages }} 页</span>
      <div class="pagination-controls">
        <button class="btn btn-sm" :disabled="query.page <= 1" @click="goPage(query.page - 1)">上一页</button>
        <button class="btn btn-sm" :disabled="query.page >= totalPages" @click="goPage(query.page + 1)">下一页</button>
      </div>
    </div>

    <!-- 弹窗 -->
    <div class="modal-overlay" v-if="modal.visible" @click="modal.visible = false">
      <div class="modal" @click.stop>
        <div class="modal-title">
          {{ modal.type === 'create' ? '新增账号' : modal.type === 'edit' ? '编辑账号' : '重置密码' }}
        </div>

        <template v-if="modal.type === 'reset'">
          <div class="form-group">
            <label class="form-label">新密码</label>
            <input v-model="modal.form.password" type="password" class="form-input" placeholder="不少于6位" />
          </div>
        </template>

        <template v-else>
          <div class="form-group">
            <label class="form-label">用户名</label>
            <input v-model.trim="modal.form.username" class="form-input" placeholder="2-20位" />
          </div>
          <div class="form-group">
            <label class="form-label">学号</label>
            <input v-model.trim="modal.form.studentNo" class="form-input" />
          </div>
          <div class="form-group">
            <label class="form-label">班级</label>
            <select v-model="modal.form.classId" class="form-select">
              <option v-for="c in classes" :key="c.id" :value="c.id">{{ c.className }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">角色</label>
            <select v-model="modal.form.role" class="form-select">
              <option :value="0">普通用户</option>
              <option :value="1">管理员</option>
            </select>
          </div>
          <div v-if="modal.type === 'create'" class="form-group">
            <label class="form-label">初始密码</label>
            <input v-model="modal.form.password" type="password" class="form-input" placeholder="不少于6位" />
          </div>
        </template>

        <div class="modal-footer">
          <button class="btn" @click="modal.visible = false">取消</button>
          <button class="btn btn-primary" @click="submit">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import {
  apiAdminUserPage,
  apiAdminUserCreate,
  apiAdminUserUpdate,
  apiAdminUserDelete,
  apiAdminResetPassword,
  apiAdminClassList
} from '@/api'
import { useToast } from '@/composables/useToast'
import type { AdminUserVO, ClassEntity, IPage } from '@/types'

const toast = useToast()
const list = ref<AdminUserVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  classId: undefined as number | undefined,
  role: undefined as number | undefined
})
const classes = ref<ClassEntity[]>([])

const modal = reactive({
  type: '' as 'create' | 'edit' | 'reset' | '',
  visible: false,
  id: 0,
  form: {
    username: '',
    studentNo: '',
    classId: undefined as number | undefined,
    password: '',
    role: 0
  }
})

const totalPages = computed(() => Math.ceil(total.value / query.size) || 1)

async function loadClasses() {
  try {
    const res = await apiAdminClassList()
    classes.value = res.data || []
  } catch (e: any) {
    toast.error(e?.message || '加载班级失败')
  }
}

async function loadList() {
  try {
    const res = await apiAdminUserPage(query)
    const p: IPage<AdminUserVO> = res.data!
    list.value = p.records
    total.value = p.total
  } catch (e: any) {
    toast.error(e?.message || '加载失败')
  }
}

function search() {
  query.page = 1
  loadList()
}

function resetSearch() {
  query.keyword = ''
  query.classId = undefined
  query.role = undefined
  query.page = 1
  loadList()
}

function openCreate() {
  modal.type = 'create'
  modal.visible = true
  modal.id = 0
  modal.form = {
    username: '',
    studentNo: '',
    classId: classes.value[0]?.id,
    password: '',
    role: 0
  }
}

function openEdit(row: AdminUserVO) {
  modal.type = 'edit'
  modal.visible = true
  modal.id = row.id
  modal.form = {
    username: row.username,
    studentNo: row.studentNo,
    classId: row.classId || undefined,
    password: '',
    role: row.role
  }
}

function openReset(row: AdminUserVO) {
  modal.type = 'reset'
  modal.visible = true
  modal.id = row.id
  modal.form.password = ''
}

async function submit() {
  try {
    if (modal.type === 'create') {
      if (!modal.form.username.trim()) {
        toast.error('用户名不能为空')
        return
      }
      if (!modal.form.studentNo.trim()) {
        toast.error('学号不能为空')
        return
      }
      if (!modal.form.classId) {
        toast.error('请选择班级')
        return
      }
      if (modal.form.password.length < 6) {
        toast.error('初始密码不少于6位')
        return
      }
      await apiAdminUserCreate({
        username: modal.form.username.trim(),
        studentNo: modal.form.studentNo.trim(),
        classId: modal.form.classId,
        password: modal.form.password,
        role: modal.form.role
      })
      toast.success('新增成功')
    } else if (modal.type === 'edit') {
      if (!modal.form.username.trim()) {
        toast.error('用户名不能为空')
        return
      }
      if (!modal.form.studentNo.trim()) {
        toast.error('学号不能为空')
        return
      }
      await apiAdminUserUpdate(modal.id, {
        username: modal.form.username.trim(),
        studentNo: modal.form.studentNo.trim(),
        classId: modal.form.classId,
        role: modal.form.role
      })
      toast.success('修改成功')
    } else if (modal.type === 'reset') {
      if (modal.form.password.length < 6) {
        toast.error('新密码不少于6位')
        return
      }
      await apiAdminResetPassword(modal.id, modal.form.password)
      toast.success('密码重置成功')
    }
    modal.visible = false
    loadList()
  } catch (e: any) {
    toast.error(e?.message || '操作失败')
  }
}

async function remove(row: AdminUserVO) {
  if (!window.confirm(`确认删除账号「${row.username}」？`)) return
  try {
    await apiAdminUserDelete(row.id)
    toast.success('删除成功')
    loadList()
  } catch (e: any) {
    toast.error(e?.message || '删除失败')
  }
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  query.page = p
  loadList()
}

onMounted(async () => {
  await loadClasses()
  await loadList()
})
</script>

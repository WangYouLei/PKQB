<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">班级管理</h2>
      <button class="btn btn-primary" @click="openCreate">+ 新增班级</button>
    </div>

    <div class="table-card">
      <table>
        <thead>
          <tr><th>ID</th><th>班级名称</th><th>创建时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.className }}</td>
            <td>{{ row.createTime }}</td>
            <td>
              <button class="btn btn-sm" @click="openEdit(row)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="remove(row)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="4" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>

    <div class="modal-overlay" v-if="modal.visible" @click="modal.visible = false">
      <div class="modal" @click.stop>
        <div class="modal-title">{{ modal.id ? '编辑班级' : '新增班级' }}</div>
        <div class="form-group">
          <label class="form-label">班级名称</label>
          <input v-model.trim="modal.form.className" class="form-input" placeholder="请输入班级名称" />
        </div>
        <div class="modal-footer">
          <button class="btn" @click="modal.visible = false">取消</button>
          <button class="btn btn-primary" @click="submit">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { apiAdminClassList, apiAdminClassCreate, apiAdminClassUpdate, apiAdminClassDelete } from '@/api'
import { useToast } from '@/composables/useToast'
import type { ClassEntity } from '@/types'

const toast = useToast()
const list = ref<ClassEntity[]>([])
const modal = reactive({ visible: false, id: 0, form: { className: '' } })

async function loadList() {
  try {
    const res = await apiAdminClassList()
    list.value = res.data || []
  } catch (e: any) {
    toast.error(e?.message || '加载失败')
  }
}

function openCreate() {
  modal.id = 0
  modal.form.className = ''
  modal.visible = true
}

function openEdit(row: ClassEntity) {
  modal.id = row.id
  modal.form.className = row.className
  modal.visible = true
}

async function submit() {
  const name = modal.form.className.trim()
  if (!name) {
    toast.error('班级名称不能为空')
    return
  }
  try {
    if (modal.id) {
      await apiAdminClassUpdate(modal.id, name)
      toast.success('修改成功')
    } else {
      await apiAdminClassCreate(name)
      toast.success('新增成功')
    }
    modal.visible = false
    loadList()
  } catch (e: any) {
    toast.error(e?.message || '操作失败')
  }
}

async function remove(row: ClassEntity) {
  if (!window.confirm(`确认删除班级「${row.className}」？`)) return
  try {
    await apiAdminClassDelete(row.id)
    toast.success('删除成功')
    loadList()
  } catch (e: any) {
    toast.error(e?.message || '删除失败')
  }
}

onMounted(loadList)
</script>

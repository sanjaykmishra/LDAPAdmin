<template>
  <div class="overflow-x-auto rounded-lg border border-gray-200">
    <table class="min-w-full divide-y divide-gray-200 text-sm">
      <thead class="bg-gray-50">
        <tr>
          <th v-if="selectable" class="px-3 py-3 w-10">
            <input type="checkbox" :checked="allSelected" :indeterminate="someSelected && !allSelected"
              @change="toggleAll" class="rounded text-blue-600 focus:ring-blue-500" />
          </th>
          <th
            v-for="col in columns"
            :key="col.key"
            class="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider"
          >{{ col.label }}</th>
          <th v-if="$slots.actions" class="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
        </tr>
      </thead>
      <tbody class="bg-white divide-y divide-gray-100">
        <tr v-if="loading">
          <td :colspan="totalCols" class="px-4 py-8 text-center text-gray-400">
            Loading…
          </td>
        </tr>
        <tr v-else-if="!rows.length">
          <td :colspan="totalCols" class="px-4 py-8 text-center text-gray-400">
            {{ emptyText }}
          </td>
        </tr>
        <tr
          v-else
          v-for="(row, i) in rows"
          :key="rowKey ? row[rowKey] : i"
          class="hover:bg-gray-50 transition-colors"
          :class="{ 'bg-blue-50': selectable && isSelected(row) }"
        >
          <td v-if="selectable" class="px-3 py-3 w-10">
            <input type="checkbox" :checked="isSelected(row)"
              @change="toggleRow(row)" class="rounded text-blue-600 focus:ring-blue-500" />
          </td>
          <td v-for="col in columns" :key="col.key" class="px-4 py-3 text-gray-700">
            <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
              {{ row[col.key] ?? '—' }}
            </slot>
          </td>
          <td v-if="$slots.actions" class="px-4 py-3 text-right">
            <slot name="actions" :row="row" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  columns: { type: Array, required: true },   // [{ key, label }]
  rows:    { type: Array, default: () => [] },
  rowKey:  { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: 'No records found.' },
  selectable: { type: Boolean, default: false },
  selectedKeys: { type: Set, default: () => new Set() },
})

const emit = defineEmits(['update:selectedKeys'])

const totalCols = computed(() => {
  let c = props.columns.length
  if (props.selectable) c++
  // We can't easily check for slot existence in computed, so use a safe estimate
  return c + 1
})

const allSelected = computed(() => props.rows.length > 0 && props.rows.every(r => props.selectedKeys.has(r[props.rowKey])))
const someSelected = computed(() => props.rows.some(r => props.selectedKeys.has(r[props.rowKey])))

function isSelected(row) {
  return props.selectedKeys.has(row[props.rowKey])
}

function toggleRow(row) {
  const key = row[props.rowKey]
  const next = new Set(props.selectedKeys)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  emit('update:selectedKeys', next)
}

function toggleAll() {
  if (allSelected.value) {
    emit('update:selectedKeys', new Set())
  } else {
    emit('update:selectedKeys', new Set(props.rows.map(r => r[props.rowKey])))
  }
}
</script>

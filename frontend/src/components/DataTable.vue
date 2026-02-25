<template>
  <div class="overflow-x-auto rounded-lg border border-gray-200">
    <table class="min-w-full divide-y divide-gray-200 text-sm">
      <thead class="bg-gray-50">
        <tr>
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
          <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="px-4 py-8 text-center text-gray-400">
            Loading…
          </td>
        </tr>
        <tr v-else-if="!rows.length">
          <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="px-4 py-8 text-center text-gray-400">
            {{ emptyText }}
          </td>
        </tr>
        <tr
          v-else
          v-for="(row, i) in rows"
          :key="rowKey ? row[rowKey] : i"
          class="hover:bg-gray-50 transition-colors"
        >
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
defineProps({
  columns: { type: Array, required: true },   // [{ key, label }]
  rows:    { type: Array, default: () => [] },
  rowKey:  { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: 'No records found.' },
})
</script>

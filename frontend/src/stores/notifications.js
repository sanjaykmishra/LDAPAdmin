import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 1

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref([])

  function push(type, message, { duration = 4000, onUndo = null } = {}) {
    const id = nextId++
    items.value.push({ id, type, message, onUndo })
    if (duration > 0) setTimeout(() => remove(id), duration)
    return id
  }

  const success = (msg, opts) => push('success', msg, typeof opts === 'object' ? opts : undefined)
  const error   = (msg) => push('error', msg, { duration: 6000 })
  const info    = (msg) => push('info', msg)

  function undo(id) {
    const item = items.value.find(n => n.id === id)
    if (item?.onUndo) {
      item.onUndo()
    }
    remove(id)
  }

  function remove(id) {
    items.value = items.value.filter(n => n.id !== id)
  }

  return { items, success, error, info, undo, remove }
})

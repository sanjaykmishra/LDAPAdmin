import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 1

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref([])

  function push(type, message, duration = 4000) {
    const id = nextId++
    items.value.push({ id, type, message })
    if (duration > 0) setTimeout(() => remove(id), duration)
    return id
  }

  const success = (msg) => push('success', msg)
  const error   = (msg) => push('error', msg, 6000)
  const info    = (msg) => push('info', msg)

  function remove(id) {
    items.value = items.value.filter(n => n.id !== id)
  }

  return { items, success, error, info, remove }
})

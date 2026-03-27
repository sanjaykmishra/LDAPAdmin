import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 1

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref([])

  function push(type, message, { duration = 4000, detail = null, action = null } = {}) {
    const id = nextId++
    const item = { id, type, message, detail, action, duration, remaining: duration }
    items.value.push(item)

    // Countdown for progress bar
    if (duration > 0) {
      const interval = 50
      const timer = setInterval(() => {
        const idx = items.value.findIndex(n => n.id === id)
        if (idx === -1) { clearInterval(timer); return }
        items.value[idx].remaining = Math.max(0, items.value[idx].remaining - interval)
        if (items.value[idx].remaining <= 0) {
          clearInterval(timer)
          remove(id)
        }
      }, interval)
    }

    // Cap at 5 visible toasts
    if (items.value.length > 5) {
      items.value = items.value.slice(-5)
    }

    return id
  }

  const success = (msg, opts) => push('success', msg, typeof opts === 'object' ? opts : undefined)
  const error   = (msg, opts) => push('error', msg, { duration: 6000, ...(typeof opts === 'object' ? opts : {}) })
  const info    = (msg, opts) => push('info', msg, typeof opts === 'object' ? opts : undefined)
  const warning = (msg, opts) => push('warning', msg, typeof opts === 'object' ? opts : undefined)

  function remove(id) {
    items.value = items.value.filter(n => n.id !== id)
  }

  return { items, success, error, info, warning, remove }
})

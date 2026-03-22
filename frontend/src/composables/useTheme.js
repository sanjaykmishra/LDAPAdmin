import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'ldapadmin-theme'

const theme = ref(localStorage.getItem(STORAGE_KEY) || 'light')

function applyTheme(value) {
  const root = document.documentElement
  if (value === 'system') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    root.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  } else {
    root.setAttribute('data-theme', value)
  }
}

/**
 * Composable for managing the app theme (light / dark / system).
 */
export function useTheme() {
  function setTheme(value) {
    theme.value = value
    localStorage.setItem(STORAGE_KEY, value)
    applyTheme(value)
  }

  onMounted(() => {
    applyTheme(theme.value)

    // Listen for system preference changes when in "system" mode
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (theme.value === 'system') applyTheme('system')
    })
  })

  return { theme, setTheme }
}

// Apply theme immediately on module load so there's no flash
applyTheme(theme.value)

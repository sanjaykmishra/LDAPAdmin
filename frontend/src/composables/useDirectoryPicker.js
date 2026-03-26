import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listDirectories } from '@/api/directories'

/**
 * Composable that provides directory picker state for superadmin pages.
 * If a dirId is in the route params, uses that. Otherwise loads the
 * directory list and provides a selectedDir ref for a picker.
 *
 * Usage:
 *   const { dirId, directories, selectedDir, loadingDirs, showPicker } = useDirectoryPicker()
 */
export function useDirectoryPicker() {
  const route = useRoute()
  const routeDirId = route.params.dirId

  const directories = ref([])
  const selectedDir = ref('')
  const loadingDirs = ref(false)

  const dirId = computed(() => routeDirId || selectedDir.value)
  const showPicker = computed(() => !routeDirId)

  onMounted(async () => {
    if (routeDirId) return // no need to load picker
    loadingDirs.value = true
    try {
      const { data } = await listDirectories()
      directories.value = data
      if (data.length === 1) selectedDir.value = data[0].id
    } catch (e) {
      console.warn('Failed to load directories:', e)
    } finally {
      loadingDirs.value = false
    }
  })

  return { dirId, directories, selectedDir, loadingDirs, showPicker }
}

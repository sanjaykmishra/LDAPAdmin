<template>
  <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
    <!-- Empty state: light gray ring -->
    <circle v-if="total === 0"
            :cx="center" :cy="center" :r="radius"
            fill="none" stroke="#e2e8f0" :stroke-width="strokeWidth" />
    <!-- Single segment: full ring in that color -->
    <circle v-else-if="arcs.length === 1"
            :cx="center" :cy="center" :r="radius"
            fill="none" :stroke="arcs[0].color" :stroke-width="strokeWidth"
            :style="{ transition: 'stroke 0.6s ease' }" />
    <!-- Multi-segment donut -->
    <circle v-else v-for="(seg, i) in arcs" :key="i"
            :cx="center" :cy="center" :r="radius"
            fill="none" :stroke="seg.color" :stroke-width="strokeWidth"
            :stroke-dasharray="seg.dashArray" :stroke-dashoffset="seg.dashOffset"
            stroke-linecap="round"
            :style="{ transition: 'stroke-dasharray 0.6s ease, stroke-dashoffset 0.6s ease' }" />
    <!-- Center text -->
    <text :x="center" :y="center" text-anchor="middle" dominant-baseline="central"
          class="fill-slate-700 text-lg font-semibold" :font-size="size * 0.18">
      {{ total }}
    </text>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  segments: { type: Array, default: () => [] },
  size: { type: Number, default: 120 },
})

const strokeWidth = computed(() => props.size * 0.15)
const radius = computed(() => (props.size - strokeWidth.value) / 2)
const center = computed(() => props.size / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const total = computed(() => props.segments.reduce((s, seg) => s + seg.value, 0))

const arcs = computed(() => {
  if (total.value === 0) return []
  let offset = circumference.value * 0.25 // start at top
  return props.segments.map(seg => {
    const pct = seg.value / total.value
    const dashLen = pct * circumference.value
    const gap = circumference.value - dashLen
    const arc = {
      color: seg.color,
      dashArray: `${dashLen} ${gap}`,
      dashOffset: -offset,
    }
    offset += dashLen
    return arc
  })
})
</script>

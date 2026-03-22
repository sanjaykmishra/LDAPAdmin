<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-xl w-full max-w-md p-8">
      <div class="text-center mb-6">
        <h1 class="text-xl font-bold text-gray-900">Registration Status</h1>
        <p class="text-sm text-gray-500 mt-1">Track the progress of your account request</p>
      </div>

      <!-- Status card -->
      <div class="bg-gray-50 rounded-lg p-5 space-y-4">
        <!-- Request info -->
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">Request ID</span>
          <code class="bg-gray-200 px-1.5 py-0.5 rounded text-xs">REQ-2026-0042</code>
        </div>
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">Submitted</span>
          <span class="text-gray-900">March 22, 2026 at 10:30 AM</span>
        </div>
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">Email</span>
          <span class="text-gray-900">john.doe@example.com</span>
        </div>

        <hr class="border-gray-200" />

        <!-- Status timeline -->
        <div class="space-y-3">
          <div class="flex items-start gap-3">
            <div class="w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center shrink-0 mt-0.5">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"/></svg>
            </div>
            <div>
              <p class="text-sm font-medium text-gray-900">Request Submitted</p>
              <p class="text-xs text-gray-500">March 22, 2026 at 10:30 AM</p>
            </div>
          </div>

          <div class="ml-3 w-px h-4 bg-gray-300"></div>

          <div class="flex items-start gap-3">
            <div class="w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center shrink-0 mt-0.5">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"/></svg>
            </div>
            <div>
              <p class="text-sm font-medium text-gray-900">Email Verified</p>
              <p class="text-xs text-gray-500">March 22, 2026 at 10:35 AM</p>
            </div>
          </div>

          <div class="ml-3 w-px h-4 bg-gray-300"></div>

          <div class="flex items-start gap-3">
            <div class="w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center shrink-0 mt-0.5 animate-pulse">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
            </div>
            <div>
              <p class="text-sm font-medium text-blue-700">Pending Approval</p>
              <p class="text-xs text-gray-500">Waiting for administrator review</p>
            </div>
          </div>

          <div class="ml-3 w-px h-4 bg-gray-200"></div>

          <div class="flex items-start gap-3">
            <div class="w-6 h-6 bg-gray-200 text-gray-400 rounded-full flex items-center justify-center shrink-0 mt-0.5">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
            </div>
            <div>
              <p class="text-sm text-gray-400">Account Created</p>
              <p class="text-xs text-gray-400">Pending</p>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-6 text-center">
        <RouterLink to="/self-service/login" class="text-sm text-blue-600 hover:text-blue-700 font-medium">
          Back to Login
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>

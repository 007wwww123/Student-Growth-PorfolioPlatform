<script setup lang="ts">
import SectionShell from '@/views/SectionShell.vue'
import AcademicRecordsPanel from '@/views/sections/AcademicRecordsPanel.vue'
</script>

<template>
  <SectionShell code="academic">
    <template #above>
      <AcademicRecordsPanel />
    </template>
  </SectionShell>
</template>

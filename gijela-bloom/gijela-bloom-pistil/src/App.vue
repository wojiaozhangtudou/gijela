<template>
  <router-view />
</template>

<script lang="ts">
import { defineComponent } from 'vue'
export default defineComponent({ name: 'App' })
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
/* ensure root occupies full height so layout 100vh calculations are accurate */

/* Status column helpers: make switches centered and give them a min-width so layout isn't cramped */
.status-cell { 
  display:flex; 
  align-items:center; 
  justify-content:center; 
  padding:4px 8px; 
  min-width: 120px;
  width: auto;
}
.status-switch { 
  min-width:100px; 
  width: auto;
}

/* Prevent switch and its labels from wrapping into multiple lines */
.status-cell, .status-switch {
  white-space: nowrap;
  overflow: visible;
}

/* Make sure status column has enough width */
.el-table .el-table__cell:has(.status-cell) {
  min-width: 140px !important;
  width: auto !important;
}

/* Responsive tweaks: on narrow screens align to left and reduce paddings */
@media (max-width: 720px) {
  .status-cell { 
    justify-content:flex-start; 
    padding-left:6px; 
    min-width: 80px;
  }
  .el-table th, .el-table td { padding: 6px 8px; }
  /* Hide switch labels on narrow screens to save space, keep only the toggle */
  .status-switch .el-switch__label { display: none; }
  .status-switch { min-width: 50px; }
  .el-table .el-table__cell:has(.status-cell) {
    min-width: 90px !important;
  }
}
</style>

<template>
  <el-container class="app-shell">
    <el-header style="display: flex; align-items: center; justify-content: space-between">
      <strong>Chat Flow</strong>
      <el-space>
        <el-button text @click="$router.push('/workflows')">流程列表</el-button>
        <el-button text @click="$router.push('/chat-workbench')">会话工作台</el-button>
      </el-space>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  overflow: hidden;
}

.app-shell {
  height: 100vh;
  overflow: hidden;
}

.app-main {
  padding: 0 !important;
  overflow: hidden;
}
</style>

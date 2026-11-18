<template>
  <el-result icon="warning" title="页面不存在" sub-title="请检查访问地址，或返回工作台">
    <template #extra>
      <el-button type="primary" @click="$router.replace('/dashboard')">返回工作台</el-button>
    </template>
  </el-result>
</template>

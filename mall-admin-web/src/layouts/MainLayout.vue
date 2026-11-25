<script setup>
import logoWhite from '../assets/logo-white.png';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { useUserStore } from '../stores/user';
import { menuRoutes } from '../router';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const collapsed = ref(false);

const activeMenu = computed(() => route.path);

const menus = computed(() =>
  menuRoutes
    .map((item) => {
      const children = (item.children || []).filter(
        (c) => !c.meta.hidden && userStore.hasAnyPerm(c.meta.perm)
      );
      return { ...item, children };
    })
    .filter((item) => item.children.length > 0 && userStore.hasAnyPerm(item.meta.perm))
);

function resolvePath(parent, child) {
  return `${parent.path}/${child.path}`.replace(/\/+/g, '/').replace(/\/$/, '');
}

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' });
      userStore.logout();
      router.replace('/login');
    } catch (e) {
      /* 取消 */
    }
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <img :src="logoWhite" alt="发士莲" class="logo-img" />
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#245234"
        text-color="#d7e6da"
        active-text-color="#ffffff"
        router
      >
        <template v-for="group in menus" :key="group.path">
          <el-sub-menu v-if="group.children.length > 1" :index="group.path">
            <template #title>
              <el-icon><component :is="group.meta.icon" /></el-icon>
              <span>{{ group.meta.title }}</span>
            </template>
            <el-menu-item
              v-for="child in group.children"
              :key="child.path"
              :index="resolvePath(group, child)"
            >
              {{ child.meta.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="resolvePath(group, group.children[0])">
            <el-icon><component :is="group.meta.icon" /></el-icon>
            <template #title>{{ group.meta.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="crumb">{{ route.meta.title }}</span>
        </div>
        <el-dropdown @command="onCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.realName }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #245234;
  transition: width 0.2s;
  overflow-x: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-img {
  height: 24px;
  width: auto;
  max-width: 100%;
  flex-shrink: 0;
}


/* 选中项用品牌辅绿点缀 */
.aside :deep(.el-menu-item.is-active) {
  background: rgba(144, 193, 86, 0.22) !important;
}

.aside :deep(.el-menu-item:hover),
.aside :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
}

.crumb {
  font-size: 15px;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.main {
  padding: 0;
  background: #f0f2f5;
  overflow-y: auto;
}
</style>

<template>
  <div class="page team-page">
    <PageHeader title="我的团队" />

    <div class="page-body">
      <div class="card tip-card">
        <van-icon name="info-o" />
        <span>仅展示自己与整棵下级团队；团队业绩奖按结算时子树汇总。</span>
      </div>

      <div class="card">
        <div class="crumb">
          <span
            v-for="(item, index) in stack"
            :key="index"
            class="crumb-item"
            :class="{ current: index === stack.length - 1 }"
            @click="backTo(index)"
          >
            {{ item.name }}
            <span v-if="index < stack.length - 1" class="muted">/</span>
          </span>
        </div>

        <van-loading v-if="loading" class="loading" />
        <template v-else>
          <div v-for="member in members" :key="member.storeId" class="member" @click="drill(member)">
            <div class="avatar">{{ (member.name || member.phone || '店').slice(0, 1) }}</div>
            <div class="info">
              <div class="name">
                {{ member.name || member.phone }}
                <van-tag v-if="!member.joined" plain>未加盟</van-tag>
                <van-tag v-else type="primary" plain>{{ member.levelName || `档位 ${member.levelId}` }}</van-tag>
              </div>
              <div class="muted small">{{ member.phone }}</div>
              <div class="muted small">{{ member.joinTime ? `加盟于 ${formatTime(member.joinTime)}` : '尚未加盟' }}</div>
            </div>
            <div class="right">
              <span v-if="member.childCount > 0" class="muted small">{{ member.childCount }} 个下级</span>
              <van-icon v-if="member.childCount > 0" name="arrow" color="#8a9099" />
            </div>
          </div>
          <div v-if="!members.length" class="text-empty">暂无下级成员</div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import { teamApi } from '../api';
import { useUserStore } from '../stores/user';
import { formatTime } from '../utils/format';

const user = useUserStore();
const stack = ref([{ storeId: null, name: '我的团队' }]);
const members = ref([]);
const loading = ref(true);

const currentParent = computed(() => stack.value[stack.value.length - 1]);

async function load() {
  loading.value = true;
  try {
    members.value = await teamApi.children(currentParent.value.storeId) || [];
  } catch (e) {
    members.value = [];
  } finally {
    loading.value = false;
  }
}

function drill(member) {
  if (!member.childCount || member.childCount <= 0) return;
  stack.value.push({ storeId: member.storeId, name: member.name || member.phone });
  load();
}

function backTo(index) {
  if (index === stack.value.length - 1) return;
  stack.value = stack.value.slice(0, index + 1);
  load();
}

onMounted(async () => {
  if (!user.profile) await user.loadProfile().catch(() => {});
  if (user.profile) {
    stack.value = [{ storeId: null, name: user.profile.name || user.profile.nickname || '我的团队' }];
  }
  load();
});
</script>

<style scoped>
.tip-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: #5b6470;
  background: #f0f6ff;
}

.loading {
  display: block;
  text-align: center;
  padding: 30px 0;
}

.crumb {
  font-size: 13px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f2f3f5;
}

.crumb-item {
  color: var(--brand);
}

.crumb-item.current {
  color: var(--text-main);
  font-weight: 600;
}

.member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f4f5f7;
}

.member:last-child {
  border-bottom: none;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: #eaf2ea;
  color: var(--brand);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.right {
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>

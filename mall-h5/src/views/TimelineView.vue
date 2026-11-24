<template>
  <div class="page timeline-page">
    <PageHeader title="历史记录" />

    <van-tabs v-model:active="type" sticky @change="reload">
      <van-tab title="全部" name="" />
      <van-tab title="加盟" name="join" />
      <van-tab title="预存" name="recharge" />
      <van-tab title="订货" name="order" />
      <van-tab title="提成" name="commission" />
      <van-tab title="业绩" name="bonus" />
      <van-tab title="提现" name="withdraw" />
    </van-tabs>

    <div class="page-body">
      <van-pull-refresh v-model="refreshing" @refresh="reload">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-for="event in events" :key="event.id" class="card event-card">
            <div class="flex-between">
              <span class="title">{{ event.title || eventTypeText(event.eventType) }}</span>
              <span v-if="event.amount !== null && event.amount !== undefined" :class="event.direction === -1 ? 'danger' : 'success'" class="amount">
                {{ event.direction === -1 ? '-' : '+' }}{{ fenToYuan(event.amount) }}
              </span>
            </div>
            <div v-if="event.detail" class="muted small mt-8">{{ event.detail }}</div>
            <div class="flex-between mt-8">
              <span class="muted small">{{ event.category || event.eventType }}</span>
              <span class="muted small">{{ formatTime(event.createdAt) }}</span>
            </div>
            <div v-if="event.bizNo" class="muted small mt-8">单号 {{ event.bizNo }}</div>
          </div>
          <div v-if="!events.length && finished" class="text-empty">暂无记录</div>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import PageHeader from '../components/PageHeader.vue';
import { timelineApi } from '../api';
import { eventTypeText, fenToYuan, formatTime } from '../utils/format';

const type = ref('');
const events = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);

async function loadMore() {
  loading.value = true;
  try {
    const result = await timelineApi.page({
      type: type.value || undefined,
      page: page.value,
      size
    });
    const records = result.records || [];
    events.value = page.value === 1 ? records : events.value.concat(records);
    finished.value = events.value.length >= result.total;
    if (!finished.value) page.value += 1;
  } catch (e) {
    finished.value = true;
  } finally {
    loading.value = false;
  }
}

function reload() {
  page.value = 1;
  finished.value = false;
  events.value = [];
  loadMore().finally(() => {
    refreshing.value = false;
  });
}
</script>

<style scoped>
.event-card {
  margin-bottom: 10px;
}

.title {
  font-size: 14px;
  font-weight: 500;
}

.amount {
  font-size: 16px;
  font-weight: 600;
}
</style>

<template>
  <div class="page notices-page">
    <PageHeader title="消息" />

    <div class="page-body">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="notice in notices" :key="notice.id" class="card notice" @click="open(notice)">
          <div class="flex-between">
            <span class="title">
              <span v-if="notice.readed !== 1" class="dot" />
              {{ notice.title }}
            </span>
            <span class="muted small">{{ formatTime(notice.createdAt) }}</span>
          </div>
          <div class="muted small content row-2">{{ notice.content }}</div>
        </div>
        <div v-if="!notices.length && finished" class="text-empty">暂无消息</div>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { showDialog } from 'vant';
import PageHeader from '../components/PageHeader.vue';
import { noticeApi } from '../api';
import { formatTime } from '../utils/format';

const notices = ref([]);
const page = ref(1);
const size = 20;
const loading = ref(false);
const finished = ref(false);

async function loadMore() {
  loading.value = true;
  try {
    const result = await noticeApi.page({ page: page.value, size });
    const records = result.records || [];
    notices.value = page.value === 1 ? records : notices.value.concat(records);
    finished.value = notices.value.length >= result.total;
    if (!finished.value) page.value += 1;
  } catch (e) {
    finished.value = true;
  } finally {
    loading.value = false;
  }
}

async function open(notice) {
  if (notice.readed !== 1) {
    noticeApi.markRead(notice.id).then(() => {
      notice.readed = 1;
    }).catch(() => {});
  }
  await showDialog({ title: notice.title, message: notice.content, confirmButtonText: '知道了' });
}
</script>

<style scoped>
.notice {
  margin-bottom: 10px;
}

.title {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--money);
  display: inline-block;
}

.content {
  margin-top: 6px;
  line-height: 1.5;
}
</style>

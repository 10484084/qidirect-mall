import { defineStore } from 'pinia';
import { cartApi } from '../api';

export const useCartStore = defineStore('cart', {
  state: () => ({
    count: 0
  }),
  actions: {
    async refresh() {
      const items = await cartApi.list();
      this.count = items.reduce((sum, item) => sum + (item.qty || 0), 0);
      return items;
    },
    reset() {
      this.count = 0;
    }
  }
});

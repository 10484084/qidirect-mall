import { defineStore } from 'pinia';
import { authApi } from '../api';
import {
  clearToken,
  getLastAccount,
  getProfileCache,
  getToken,
  setLastAccount,
  setProfileCache,
  setToken
} from '../utils/token';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    profile: getProfileCache()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    perms: (state) => (state.profile && state.profile.perms) || [],
    realName: (state) => (state.profile && (state.profile.realName || state.profile.account)) || '',
    account: (state) => (state.profile && state.profile.account) || '',
    phone: (state) => (state.profile && state.profile.phone) || '',
    lastAccount: () => getLastAccount()
  },
  actions: {
    setPhone(phone) {
      if (!this.profile) this.profile = {};
      this.profile = { ...this.profile, phone };
      setProfileCache(this.profile);
    },
    hasPerm(perm) {
      if (!perm) return true;
      const list = this.perms;
      return list.includes('*') || list.includes(perm);
    },
    async login(account, password) {
      const data = await authApi.login({ account, password });
      this.token = data.token;
      this.profile = {
        adminId: data.adminId,
        account: data.account,
        realName: data.realName,
        phone: data.phone || '',
        perms: data.perms || []
      };
      setToken(data.token);
      setProfileCache(this.profile);
      setLastAccount(account);
      return data;
    },
    logout() {
      this.token = '';
      this.profile = null;
      clearToken();
    }
  }
});

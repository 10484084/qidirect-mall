import { defineStore } from 'pinia';
import { authApi } from '../api';
import { clearToken, getProfileCache, getToken, setProfileCache, setToken } from '../utils/token';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    profile: getProfileCache()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    perms: (state) => (state.profile && state.profile.perms) || [],
    realName: (state) => (state.profile && (state.profile.realName || state.profile.account)) || ''
  },
  actions: {
    hasPerm(perm) {
      if (!perm) return true;
      const list = this.perms;
      return list.includes('*') || list.includes(perm);
    },
    hasAnyPerm(permList) {
      if (!permList || !permList.length) return true;
      return permList.some((p) => this.hasPerm(p));
    },
    async login(account, password) {
      const data = await authApi.login({ account, password });
      this.token = data.token;
      this.profile = {
        adminId: data.adminId,
        account: data.account,
        realName: data.realName,
        perms: data.perms || []
      };
      setToken(data.token);
      setProfileCache(this.profile);
      return data;
    },
    logout() {
      this.token = '';
      this.profile = null;
      clearToken();
    }
  }
});

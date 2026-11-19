import { defineStore } from 'pinia';
import { authApi, storeApi } from '../api';
import { clearToken, getToken, setToken } from '../utils/token';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    profile: null,
    eligibility: null
  }),
  getters: {
    loggedIn: (state) => Boolean(state.token),
    joined: (state) => Boolean(state.profile && state.profile.levelId && state.profile.levelId > 0),
    authed: (state) => Boolean(state.profile && state.profile.authStatus === 1)
  },
  actions: {
    applyLogin(data) {
      setToken(data.token);
      this.token = data.token;
      this.profile = null;
      this.eligibility = null;
    },
    async login(payload) {
      // 用户名密码登录（username 默认为手机号，由后台设置密码）
      const data = await authApi.pwdLogin(payload);
      this.applyLogin(data);
      await this.loadProfile();
      return data;
    },
    async register(payload) {
      const data = await authApi.register(payload);
      this.applyLogin(data);
      await this.loadProfile();
      return data;
    },
    async loadProfile() {
      this.profile = await storeApi.profile();
      return this.profile;
    },
    async loadEligibility() {
      this.eligibility = await storeApi.eligibility();
      return this.eligibility;
    },
    logout() {
      clearToken();
      this.token = '';
      this.profile = null;
      this.eligibility = null;
    }
  }
});

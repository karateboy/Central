import axios from 'axios';

export default {
  namespaced: true,
  state: {
    login: false,
    userInfo: {
      _id: '',
      name: '',
      phone: '',
      isAdmin: false,
      group: '',
      monitorTypeOfInterest: [],
    },
    group: {},
  },
  getters: {},
  mutations: {
    setUserInfo(state, val) {
      state.userInfo._id = val._id;
      state.userInfo.name = val.name;
      state.userInfo.isAdmin = val.isAdmin;
      state.userInfo.group = val.group;
      state.userInfo.monitorTypeOfInterest = val.monitorTypeOfInterest;
    },
    setLogin(state, val) {
      state.login = val;
    },
    setGroup(state, val) {
      state.group = val;
    },
  },

  actions: {
    async getUserInfo({ commit, state }) {
      try {
        const res = await axios.get(`/User/${state.userInfo._id}`);
        const payload = res && res.data;
        commit('setUserInfo', payload);
      } catch (err) {
        throw new Error(err);
      }
    },
  },
};

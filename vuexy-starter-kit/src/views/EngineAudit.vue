<template>
  <div>
    <b-card no-body>
      <b-tabs
        pills
        fill
        card
        content-class="my-card-content"
        nav-class="my-card-header"
      >
        <b-tab title="參數設定">
          <b-form-group label="逆風角度" label-for="DU" label-cols-md="3">
            <b-form-input
              v-model.number="form.setting.directionOffsetThreshold"
              type="number"
            ></b-form-input>
          </b-form-group>
          <b-form-group label="高低風速判斷" label-for="LH" label-cols-md="3">
            <b-form-input
              v-model.number="form.setting.windSpeedThreshold"
              type="number"
            ></b-form-input>
          </b-form-group>
          <b-form-group
            label="轉向速度差判斷"
            label-for="TurnSpeed"
            label-cols-md="3"
          >
            <b-form-input
              v-model.number="form.setting.turnSpeedThreshold"
              type="number"
            ></b-form-input>
          </b-form-group>
          <b-form-group
            label="轉向角度判斷"
            label-for="TurnDirection"
            label-cols-md="3"
          >
            <b-form-input
              v-model.number="form.setting.turnDirectionThreshold"
              type="number"
            ></b-form-input>
          </b-form-group>
        </b-tab>
        <b-tab title="註記資料區間">
          <b-form-group label="測點" label-for="monitor" label-cols-md="3">
            <v-select
              id="monitor"
              v-model="form.monitors"
              label="desc"
              :reduce="m => m._id"
              :options="monitorOfNoEPA"
              :close-on-select="false"
              multiple
            />
          </b-form-group>
          <b-form-group label="測項" label-for="monitorType" label-cols-md="3">
            <v-select
              id="monitorType"
              v-model="form.monitorTypes"
              label="desp"
              :reduce="mt => mt._id"
              :options="activatedMonitorTypes"
              :close-on-select="false"
              multiple
            />
          </b-form-group>
          <b-form-group
            label="資料區間"
            label-for="dataRange"
            label-cols-md="3"
          >
            <date-picker
              id="dataRange"
              v-model="form.range"
              :range="true"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-type="timestamp"
              :show-second="false"
            />
          </b-form-group>
          <b-row>
            <b-col offset-md="3">
              <b-button variant="gradient-primary" class="mr-1" @click="audit">
                開始註記
              </b-button>
              <b-button variant="gradient-success" class="mr-1" @click="revert">
                復原註記
              </b-button>
            </b-col>
          </b-row>
        </b-tab>
      </b-tabs>
    </b-card>
  </div>
</template>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<style scoped>
.my-card-header {
  padding: 1.5rem 1.5rem 0rem 1.5rem;
}
.my-card-content {
  padding: 1.5rem 1.5rem 0rem 1.5rem;
}
</style>
<script lang="ts">
import Vue from 'vue';
import vSelect from 'vue-select';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
const Ripple = require('vue-ripple-directive');
import { mapGetters, mapActions, mapMutations } from 'vuex';
import moment from 'moment';
import axios from 'axios';
interface EngineAuditSetting {
  directionOffsetThreshold: number;
  windSpeedThreshold: number;
  turnSpeedThreshold: number;
  turnDirectionThreshold: number;
}

interface EngineAuditParam {
  setting: EngineAuditSetting;
  monitors: Array<string>;
  monitorTypes: Array<string>;
  tabType: string;
  range: Array<number>;
}

export default Vue.extend({
  components: {
    vSelect,
    DatePicker,
  },
  directives: {
    Ripple,
  },

  data() {
    const range = [
      moment().subtract(1, 'hour').minute(0).second(0).millisecond(0).valueOf(),
      moment().minute(0).second(0).millisecond(0).valueOf(),
    ];
    let setting: EngineAuditSetting = {
      directionOffsetThreshold: 60,
      windSpeedThreshold: 7,
      turnSpeedThreshold: 0.8,
      turnDirectionThreshold: 15,
    };
    let form: EngineAuditParam = {
      setting,
      monitors: Array<string>(),
      monitorTypes: Array<string>(),
      tabType: 'min',
      range,
    };
    return {
      form,
    };
  },
  computed: {
    ...mapGetters('monitors', ['mMap', 'monitorOfNoEPA']),
    ...mapGetters('monitorTypes', ['mtMap', 'activatedMonitorTypes']),
  },
  async mounted() {
    await this.fetchMonitors();
    await this.fetchMonitorTypes();
    for (const m of this.monitorOfNoEPA) this.form.monitors.push(m._id);
    if (this.activatedMonitorTypes.length !== 0)
      this.form.monitorTypes.push(this.activatedMonitorTypes[0]._id);
  },
  methods: {
    ...mapActions('monitors', ['fetchMonitors']),
    ...mapActions('monitorTypes', ['fetchMonitorTypes']),
    ...mapMutations(['setLoading']),
    async audit() {
      const url = '/EngineAudit';
      this.setLoading({ loading: true });
      try {
        const res = await axios.post(url, this.form);
        if (res.status === 200) {
          let ret = res.data;
          this.$bvModal.msgBoxOk(`引擎排放註記完成 (${ret.count}筆狀態被註記)`);
        }
      } catch (err) {
        throw new Error('failed to recalculate hour');
      } finally {
        this.setLoading({ loading: false });
      }
    },
    async revert() {
      const url = '/RevertEngineAudit';
      this.setLoading({ loading: true });
      try {
        const res = await axios.post(url, this.form);
        if (res.status === 200) {
          let ret = res.data;
          this.$bvModal.msgBoxOk(`已復原引擎排放註記 (${ret.count}筆被復原)`);
        }
      } catch (err) {
        throw new Error('failed to reveert audit');
      } finally {
        this.setLoading({ loading: false });
      }
    },
  },
});
</script>

<style></style>

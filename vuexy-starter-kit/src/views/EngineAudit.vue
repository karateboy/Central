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
          <b-row no-gutters>
            <b-col cols="12">
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
            </b-col>
            <b-col cols="12">
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
            </b-col>
            <!-- submit and reset -->
            <b-col offset-md="3">
              <b-button
                variant="gradient-primary"
                class="mr-1"
                @click="recalculate"
              >
                開始註記
              </b-button>
              <b-button
                variant="gradient-success"
                class="mr-1"
                @click="recalculate"
              >
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
import { mapState, mapGetters, mapActions } from 'vuex';
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
      monitors: Array<any>(),
      range,
    };
    return {
      form,
    };
  },
  computed: {
    ...mapState('monitors', ['monitors']),
    ...mapGetters('monitors', ['mMap', 'monitorOfNoEPA']),
  },
  async mounted() {
    await this.fetchMonitors();

    for (const m of this.monitorOfNoEPA) this.form.monitors.push(m._id);
  },
  methods: {
    ...mapActions('monitors', ['fetchMonitors']),
    async recalculate() {
      const monitors = this.form.monitors.join(':');
      const url = `/Recalculate/${monitors}/${this.form.range[0]}/${this.form.range[1]}`;

      try {
        const res = await axios.get(url);
        if (res.data.ok) {
          this.$bvModal.msgBoxOk('開始重新計算小時值');
        }
      } catch (err) {
        throw new Error('failed to recalculate hour');
      }
    },
  },
});
</script>

<style></style>

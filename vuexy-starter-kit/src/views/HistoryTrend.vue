<template>
  <div>
    <b-card>
      <b-form @submit.prevent>
        <b-form-group label="測點" label-for="monitor" label-cols-md="3">
          <v-select
            id="monitor"
            v-model="form.monitors"
            label="desc"
            :reduce="mt => mt._id"
            :options="monitors"
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
            multiple
            :close-on-select="false"
          />
        </b-form-group>
        <b-form-group label="時間單位" label-for="reportUnit" label-cols-md="3">
          <v-select
            id="reportUnit"
            v-model="form.reportUnit"
            label="txt"
            :reduce="dt => dt.id"
            :options="reportUnits"
          />
        </b-form-group>
        <b-form-group label="狀態" label-for="statusFilter" label-cols-md="3">
          <v-select
            id="statusFilter"
            v-model="form.statusFilter"
            label="txt"
            :reduce="dt => dt.id"
            :options="statusFilters"
          />
        </b-form-group>
        <b-form-group label="圖表類型" label-for="chartType" label-cols-md="3">
          <v-select
            id="chartType"
            v-model="form.chartType"
            label="desc"
            :reduce="ct => ct.type"
            :options="chartTypes"
          />
        </b-form-group>
        <b-form-group label="資料區間" label-for="dataRange" label-cols-md="3">
          <date-picker
            id="dataRange"
            v-model="form.range"
            :range="true"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-type="timestamp"
            :show-second="false"
          />
          <b-button
            variant="gradient-primary"
            class="ml-1"
            size="md"
            @click="setToday"
            >今天</b-button
          >
          <b-button
            variant="gradient-primary"
            class="ml-1"
            size="md"
            @click="setLast2Days"
            >前兩天</b-button
          >
          <b-button
            variant="gradient-primary"
            class="ml-1"
            size="md"
            @click="set3DayBefore"
            >前三天</b-button
          >
        </b-form-group>
        <b-row>
          <b-col offset-md="3">
            <b-button
              v-ripple.400="'rgba(255, 255, 255, 0.15)'"
              type="submit"
              variant="primary"
              class="mr-1"
              @click="query"
            >
              查詢
            </b-button>
            <b-button
              v-ripple.400="'rgba(255, 255, 255, 0.15)'"
              type="submit"
              variant="primary"
              class="mr-1"
              @click="downloadExcel"
            >
              下載Excel
            </b-button>
          </b-col>
        </b-row>
      </b-form>
    </b-card>
    <b-card v-show="display">
      <b-card-body><div id="chart_container" /></b-card-body>
    </b-card>
  </div>
</template>
<style>
.highcharts-container,
.highcharts-container svg {
  width: 100% !important;
}
</style>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<script lang="ts">
import Vue from 'vue';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
const Ripple = require('vue-ripple-directive');
import { mapState, mapActions, mapMutations, mapGetters } from 'vuex';
import darkTheme from 'highcharts/themes/dark-unica';
import useAppConfig from '../@core/app-config/useAppConfig';
import moment from 'moment';
import axios from 'axios';
import highcharts from 'highcharts';
import { Group } from './types';

export default Vue.extend({
  components: {
    DatePicker,
  },
  directives: {
    Ripple,
  },

  data() {
    let range = [moment().subtract(1, 'days').valueOf(), moment().valueOf()];
    return {
      statusFilters: [
        { id: 'all', txt: '全部' },
        { id: 'normal', txt: '正常量測值' },
        { id: 'calbration', txt: '校正' },
        { id: 'maintance', txt: '維修' },
        { id: 'invalid', txt: '無效數據' },
        { id: 'valid', txt: '有效數據' },
      ],
      reportUnits: [
        { txt: '秒', id: 'Sec' },
        { txt: '分', id: 'Min' },
        { txt: '六分', id: 'SixMin' },
        { txt: '十分', id: 'TenMin' },
        { txt: '十五分', id: 'FifteenMin' },
        { txt: '小時', id: 'Hour' },
        { txt: '天', id: 'Day' },
        { txt: '月', id: 'Month' },
        { txt: '季', id: 'Quarter' },
        { txt: '年', id: 'Year' },
      ],
      reportUnit: 'Hour',
      display: false,
      chartTypes: [
        {
          type: 'line',
          desc: '折線圖',
        },
        {
          type: 'spline',
          desc: '曲線圖',
        },
        {
          type: 'area',
          desc: '面積圖',
        },
        {
          type: 'areaspline',
          desc: '曲線面積圖',
        },
        {
          type: 'column',
          desc: '柱狀圖',
        },
        {
          type: 'scatter',
          desc: '點圖',
        },
      ],
      form: {
        monitors: Array<string>(),
        monitorTypes: Array<string>(),
        reportUnit: 'Hour',
        statusFilter: 'all',
        chartType: 'line',
        range,
      },
    };
  },
  computed: {
    ...mapState('user', ['group']),
    ...mapState('monitorTypes', ['monitorTypes']),
    ...mapGetters('monitorTypes', ['activatedMonitorTypes']),
    ...mapState('monitors', ['monitors']),
  },
  watch: {},
  async mounted() {
    const { skin } = useAppConfig();
    if (skin.value == 'dark') {
      darkTheme(highcharts);
    }

    await this.fetchMonitorTypes();
    await this.fetchMonitors();

    if (this.activatedMonitorTypes.length !== 0)
      this.form.monitorTypes.push(this.activatedMonitorTypes[0]._id);

    if (this.monitors.length !== 0) {
      this.form.monitors.push(this.monitors[0]._id);
    }

    if (this.group) {
      let group = this.group as Group;
      if (group.delayHour) {
        this.form.range = [
          moment()
            .subtract(1, 'days')
            .subtract(group.delayHour, 'hours')
            .valueOf(),
          moment().subtract(group.delayHour, 'hours').valueOf(),
        ];
      }
    }
  },
  methods: {
    ...mapActions('monitorTypes', ['fetchMonitorTypes']),
    ...mapActions('monitors', ['fetchMonitors']),
    ...mapMutations(['setLoading']),
    setToday() {
      this.form.range = [moment().startOf('day').valueOf(), moment().valueOf()];
    },
    setLast2Days() {
      const last2days = moment().subtract(2, 'day');
      this.form.range = [
        last2days.startOf('day').valueOf(),
        moment().valueOf(),
      ];
    },
    set3DayBefore() {
      const threeDayBefore = moment().subtract(3, 'day');
      this.form.range = [
        threeDayBefore.startOf('day').valueOf(),
        moment().valueOf(),
      ];
    },
    async query() {
      this.setLoading({ loading: true });
      this.display = true;
      const monitors = this.form.monitors.join(':');
      const url = `/HistoryTrend/${monitors}/${this.form.monitorTypes.join(
        ':',
      )}/${this.form.reportUnit}/${this.form.statusFilter}/${
        this.form.range[0]
      }/${this.form.range[1]}`;
      const res = await axios.get(url);
      const ret = res.data as highcharts.Options;

      this.setLoading({ loading: false });
      ret.chart = {
        type: this.form.chartType,
        zoomType: 'xy',
        panning: {
          enabled: true,
          type: 'xy',
        },
        panKey: 'shift',
        alignTicks: false,
      };

      const pointFormatter = function pointFormatter(this: any) {
        const d = new Date(this.x);
        return `${d.toLocaleString()}:${Math.round(this.y)}度`;
      };

      ret.tooltip = { valueDecimals: 2 };
      ret.legend = { enabled: true };
      ret.credits = {
        enabled: false,
        href: 'http://www.wecc.com.tw/',
      };
      let xAxis = ret.xAxis as highcharts.XAxisOptions;
      xAxis.type = 'datetime';
      xAxis.dateTimeLabelFormats = {
        day: '%b%e日',
        week: '%b%e日',
        month: '%y年%b',
      };

      ret.plotOptions = {
        scatter: {
          tooltip: {
            pointFormatter,
          },
        },
      };
      ret.time = {
        timezoneOffset: -480,
      };

      highcharts.chart('chart_container', ret);
    },
    async downloadExcel() {
      const baseUrl =
        process.env.NODE_ENV === 'development' ? 'http://localhost:9000/' : '/';
      const monitors = this.form.monitors.join(':');
      const url = `${baseUrl}HistoryTrend/excel/${monitors}/${this.form.monitorTypes.join(
        ':',
      )}/${this.form.reportUnit}/${this.form.statusFilter}/${
        this.form.range[0]
      }/${this.form.range[1]}`;

      window.open(url);
    },
  },
});
</script>

<style></style>

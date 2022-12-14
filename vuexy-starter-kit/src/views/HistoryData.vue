<template>
  <div>
    <b-card>
      <b-form @submit.prevent>
        <b-row>
          <b-col cols="12">
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
          </b-col>
          <b-col cols="12">
            <b-form-group
              label="測項"
              label-for="monitorType"
              label-cols-md="3"
            >
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
          </b-col>
          <b-col cols="12">
            <b-form-group
              label="資料種類"
              label-for="dataType"
              label-cols-md="3"
            >
              <v-select
                id="dataType"
                v-model="form.dataType"
                label="txt"
                :reduce="dt => dt.id"
                :options="dataTypes"
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
          </b-col>
          <!-- submit and reset -->
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
              v-ripple.400="'rgba(186, 191, 199, 0.15)'"
              type="reset"
              class="mr-1"
              variant="outline-secondary"
            >
              取消
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
    <b-card v-show="display" :title="resultTitle">
      <b-table
        striped
        hover
        :fields="columns"
        :items="rows"
        show-empty
        :per-page="15"
        :current-page="currentPage"
        responsive
        sticky-header="800px"
      >
        <template #thead-top>
          <b-tr>
            <b-th></b-th>
            <b-th
              v-for="mt in form.monitorTypes"
              :key="mt"
              :colspan="form.monitors.length"
              class="text-center"
              style="text-transform: none"
              >{{ getMtDesc(mt) }}</b-th
            >
          </b-tr>
        </template>
      </b-table>
      <b-pagination
        v-model="currentPage"
        :total-rows="rows.length"
        :per-page="15"
        first-text="⏮"
        prev-text="⏪"
        next-text="⏩"
        last-text="⏭"
        class="mt-4"
      ></b-pagination>
    </b-card>
  </div>
</template>
<script lang="ts">
import Vue from 'vue';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
const Ripple = require('vue-ripple-directive');
import { mapState, mapGetters, mapActions, mapMutations } from 'vuex';
import { Group } from './types';
import moment from 'moment';
import axios from 'axios';
const excel = require('../libs/excel');
const _ = require('lodash');

export default Vue.extend({
  components: {
    DatePicker,
  },
  directives: {
    Ripple,
  },

  data() {
    const range = [moment().subtract(1, 'days').valueOf(), moment().valueOf()];
    return {
      dataTypes: [
        { txt: '小時資料', id: 'hour' },
        { txt: '分鐘資料', id: 'min' },
      ],
      form: {
        monitors: Array<any>(),
        monitorTypes: Array<any>(),
        dataType: 'hour',
        range,
      },
      display: false,
      columns: Array<any>(),
      rows: Array<any>(),
      currentPage: 1,
    };
  },
  computed: {
    ...mapState('monitorTypes', ['monitorTypes']),
    ...mapState('monitors', ['monitors']),
    ...mapGetters('monitorTypes', ['mtMap', 'activatedMonitorTypes']),
    ...mapGetters('monitors', ['mMap']),
    ...mapState('user', ['group']),
    resultTitle(): string {
      return `總共${this.rows.length}筆`;
    },
  },
  watch: {},
  async mounted() {
    await this.fetchMonitorTypes();
    await this.fetchMonitors();

    if (this.monitors.length !== 0) {
      this.form.monitors.push(this.monitors[0]._id);
    }

    if (this.activatedMonitorTypes.length !== 0)
      this.form.monitorTypes.push(this.activatedMonitorTypes[0]._id);

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
      this.rows = [];
      this.columns = this.getColumns();
      const monitors = this.form.monitors.join(':');
      const monitorTypes = this.form.monitorTypes.join(':');
      const url = `/HistoryReport/${monitors}/${monitorTypes}/${this.form.dataType}/${this.form.range[0]}/${this.form.range[1]}`;

      const ret = await axios.get(url);
      this.setLoading({ loading: false });
      for (const row of ret.data.rows) {
        row.date = moment(row.date).format('lll');
      }

      this.rows = ret.data.rows;
    },
    cellDataTd(i: number) {
      return (_value: any, _key: any, item: any) =>
        item.cellData[i].cellClassName;
    },
    getMtDesc(mt: string) {
      const mtCase = this.mtMap.get(mt);
      return `${mtCase.desp}(${mtCase.unit})`;
    },
    getColumns() {
      const ret = [];
      ret.push({
        key: 'date',
        label: '時間',
        stickyColumn: true,
      });
      let i = 0;
      for (const mt of this.form.monitorTypes) {
        const mtCase = this.mtMap.get(mt);
        for (const m of this.form.monitors) {
          const mCase = this.mMap.get(m);
          ret.push({
            key: `cellData[${i}].v`,
            label: `${mCase.desc}`,
            tdClass: this.cellDataTd(i),
          });
          i++;
        }
      }

      return ret;
    },
    async downloadExcel() {
      const baseUrl =
        process.env.NODE_ENV === 'development' ? 'http://localhost:9000/' : '/';
      const monitors = this.form.monitors.join(':');
      let reportUnit = 'Min';
      if (this.form.dataType === 'hour') reportUnit = 'Hour';

      const url = `${baseUrl}HistoryTrend/excel/${monitors}/${this.form.monitorTypes.join(
        ':',
      )}/${reportUnit}/all/${this.form.range[0]}/${this.form.range[1]}`;

      window.open(url);
    },
    exportExcel() {
      const title = this.columns.map(e => e.label);
      const key = this.columns.map(e => e.key);
      for (let entry of this.rows) {
        let e = entry as any;
        for (let k of key) {
          e[k] = _.get(entry, k);
        }
      }
      const monitorTypes = this.form.monitorTypes.join('');
      const params = {
        title,
        key,
        data: this.rows,
        autoWidth: true,
        filename: `${monitorTypes}歷史資料查詢`,
      };
      excel.export_array_to_excel(params);
    },
  },
});
</script>

<style></style>

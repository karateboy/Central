<template>
  <b-card title="測站即時資訊" class="text-center">
    <b-table
      striped
      hover
      :fields="fields"
      :items="recordLists"
      responsive
      small
    >
      <template #cell(_id)="data">
        <p>
          <strong>{{ getMonitorName(data.item._id) }}</strong>
        </p>
        <i>{{ getUpdateTime(data.item._id) }}</i>
      </template>
    </b-table>
  </b-card>
</template>
<script lang="ts">
import axios from 'axios';
import { MonitorType, MtRecord, RecordList, RecordListID } from './types';
import { Monitor } from '../store/monitors/types';
import { mapGetters, mapActions, mapState } from 'vuex';
import moment from 'moment';
import {
  BvTableFieldArray,
  BvTableField,
} from 'bootstrap-vue/src/components/table/index';
interface DisplayRecordList extends RecordList {
  recordMap?: Map<string, MtRecord>;
}

interface LatestMonitorData {
  monitorTypes: Array<string>;
  monitorData: Array<RecordList>;
}

import Vue from 'vue';
export default Vue.extend({
  data() {
    let recordLists = Array<DisplayRecordList>();
    let monitorTypes = Array<string>();
    let timer = 0;
    return {
      monitorTypes,
      recordLists,
      timer,
    };
  },
  computed: {
    ...mapState('monitors', ['monitors', 'activeID']),
    ...mapGetters('monitorTypes', ['mtMap']),
    ...mapGetters('monitors', ['mMap']),
    fields(): BvTableFieldArray {
      let ret = Array<{ key: string } & BvTableField>();
      ret.push({
        key: '_id',
        label: '測站',
        isRowHeader: true,
        sortable: true,
      });
      ret.push({
        key: 'distance',
        label: '離港距離 (km)',
        sortable: true,
        formatter: (_, __, item: DisplayRecordList) => {
          let mMap = this.mMap as Map<string, Monitor>;
          let monitor = mMap.get(item._id.monitor);
          let latRecord = item.recordMap!.get('LAT');
          let lngRecord = item.recordMap!.get('LNG');
          if (
            monitor === undefined ||
            monitor.lat === undefined ||
            monitor.lng === undefined ||
            latRecord === undefined ||
            latRecord.value === undefined ||
            lngRecord === undefined ||
            lngRecord.value === undefined
          )
            return '-';

          let distance = this.getDistanceFromLatLonInKm(
            monitor.lat,
            monitor.lng,
            latRecord?.value,
            lngRecord?.value,
          );
          return `${distance?.toFixed(1)}`;
        },
      });
      for (let mt of this.monitorTypes) {
        let mtCase = this.mtMap.get(mt) as MonitorType;
        ret.push({
          key: mt,
          label: `${mtCase.desp}\n(${mtCase.unit})`,
          formatter: (_, mt: string, item: DisplayRecordList) => {
            let mtData = item.recordMap?.get(mt);
            if (!mtData) return '-';

            if (mtData.value === undefined) return '-';

            return `${mtData.value.toFixed(mtCase.prec)}`;
          },
          thClass: ['text-wrap'],
          thStyle: 'text-transform: none',
          sortable: true,
          tdClass: (value: any, key: string, item: DisplayRecordList) => {
            let mtCase = this.mtMap.get(mt) as MonitorType;
            let mtData = item.recordMap?.get(mt);
            if (
              mtCase.std_law &&
              mtData &&
              mtData.value &&
              mtCase.std_law <= mtData.value
            )
              return 'bg-danger';
            else return '';
          },
          tdAttr: (value: any, key: string, item: DisplayRecordList) => {
            let mtCase = this.mtMap.get(mt) as MonitorType;
            let mtData = item.recordMap?.get(mt);
            if (
              mtCase.std_law &&
              mtData &&
              mtData.value &&
              mtCase.std_law <= mtData.value
            )
              return {
                'v-b-tooltip.hover': true,
                title: '超過法規值',
              };
            else return {};
          },
        });
      }
      return ret;
    },
  },
  async mounted() {
    await this.fetchMonitorTypes();
    await this.fetchMonitors();
    this.getMonitorRealtimeData();
    let me = this;
    this.timer = setInterval(me.getMonitorRealtimeData, 60 * 1000);
  },
  beforeDestroy() {
    clearInterval(this.timer);
  },
  methods: {
    ...mapActions('monitorTypes', ['fetchMonitorTypes']),
    ...mapActions('monitors', ['fetchMonitors']),
    async getMonitorRealtimeData() {
      const ret = await axios.get('/LatestMonitorData');
      let data = ret.data as LatestMonitorData;
      this.monitorTypes = data.monitorTypes;
      this.recordLists = data.monitorData;
      for (let recordList of this.recordLists) {
        recordList.recordMap = new Map<string, MtRecord>();
        for (let mtData of recordList.mtDataList) {
          recordList.recordMap.set(mtData.mtName, mtData);
        }
      }
    },
    getMonitorName(_id: RecordListID): string {
      return `${this.mMap.get(_id.monitor).desc}`;
    },
    getUpdateTime(_id: RecordListID): string {
      return `${moment(_id.time).fromNow()}`;
    },
    getDistanceFromLatLonInKm(
      lat1?: number,
      lon1?: number,
      lat2?: number,
      lon2?: number,
    ): number | undefined {
      function deg2rad(deg: number) {
        return deg * (Math.PI / 180);
      }
      if (
        lat1 === undefined ||
        lat2 === undefined ||
        lon1 === undefined ||
        lon2 === undefined
      )
        return undefined;

      let R = 6371; // Radius of the earth in km
      let dLat = deg2rad(lat2 - lat1); // deg2rad below
      let dLon = deg2rad(lon2 - lon1);
      let a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(deg2rad(lat1)) *
          Math.cos(deg2rad(lat2)) *
          Math.sin(dLon / 2) *
          Math.sin(dLon / 2);
      let c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      let d = R * c; // Distance in km
      return d;
    },
  },
});
</script>

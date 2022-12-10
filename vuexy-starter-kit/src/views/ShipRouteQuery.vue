<template>
  <div>
    <b-card>
      <b-form @submit.prevent>
        <b-form-group label="測點" label-for="monitor" label-cols-md="3">
          <v-select
            id="monitor"
            v-model="form.monitor"
            label="desc"
            :reduce="mt => mt._id"
            :options="monitorOfNoEPA"
          />
        </b-form-group>
        <b-form-group label="資料種類" label-for="dataType" label-cols-md="3">
          <v-select
            id="dataType"
            v-model="form.dataType"
            label="txt"
            :reduce="dt => dt.id"
            :options="dataTypes"
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
        </b-form-group>
        <b-form-group
          label="測項濃度"
          label-for="monitorType"
          label-cols-md="3"
        >
          <b-row>
            <b-col>
              <v-select
                id="monitorType"
                v-model="form.monitorType"
                label="desp"
                :reduce="mt => mt._id"
                :options="activatedMonitorTypes"
              />
            </b-col>
            <b-col class="align-middle">
              <b-form-checkbox v-model="form.ais"
                >顯示AIS軌跡 (點擊船隻圖示顯示個別軌跡)</b-form-checkbox
              >
            </b-col>
          </b-row>
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
              v-ripple.400="'rgba(186, 191, 199, 0.15)'"
              class="mr-1"
              type="reset"
              variant="outline-secondary"
            >
              取消
            </b-button>
          </b-col>
        </b-row>
      </b-form>
    </b-card>

    <b-card
      v-show="displayRoute"
      border-variant="primary"
      :header="shipRouteTitle"
      header-tag="h2"
    >
      <b-row>
        <b-col cols="2">
          <b-form-checkbox v-model="form.shipRoute"
            >顯示監測船軌跡</b-form-checkbox
          >
        </b-col>
        <b-col>
          <b-form-group label="濃度圖類型:" label-cols-md="2">
            <b-row>
              <b-col cols="3">
                <b-form-radio-group
                  id="graph-type"
                  v-model="form.graphType"
                  :options="graphOptions"
                  stacked
                  name="graph-type"
                ></b-form-radio-group>
              </b-col>
              <b-col v-if="form.graphType === 'heatmap'">
                <b-form-checkbox v-model="heatmapOption.dissipating">
                  縮放地圖不加強擴散效果
                </b-form-checkbox>
              </b-col>
            </b-row>
          </b-form-group>
        </b-col>
      </b-row>
      <div class="map_container">
        <GmapMap
          ref="mapRef"
          :center="mapCenter"
          :zoom="13"
          map-type-id="roadmap"
          class="map_canvas"
          :options="mapOption"
        >
          <div id="mapLegend" class="mb-2 rounded bg-white border">
            <b-table-simple v-show="form.graphType === 'bar'" small>
              <b-thead>
                <b-tr class="text-center">
                  <b-th colspan="2">圖例</b-th>
                </b-tr>
              </b-thead>
              <b-tbody>
                <b-tr>
                  <b-td :style="{ color: 'red' }" class="bg-light"
                    ><strong>___</strong></b-td
                  >
                  <b-td>監測(海巡)軌跡</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'green' }"></b-td>
                  <b-td>{{ getLevelExplain(0) }}</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'yellow' }"></b-td>
                  <b-td>{{ getLevelExplain(1) }}</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'orange' }"></b-td>
                  <b-td>{{ getLevelExplain(2) }}</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'red' }"></b-td>
                  <b-td>{{ getLevelExplain(3) }}</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'purple' }"></b-td>
                  <b-td>{{ getLevelExplain(4) }}</b-td>
                </b-tr>
                <b-tr>
                  <b-td :style="{ 'background-color': 'maroon' }"></b-td>
                  <b-td>{{ getLevelExplain(5) }}</b-td>
                </b-tr>
              </b-tbody>
            </b-table-simple>
          </div>
          <div v-if="mapLoaded">
            <GmapMarker
              v-if="mapLoaded"
              key="master"
              :position="mapCenter"
              :clickable="true"
              :icon="masterShipIcon"
            />
            <GmapPolyline
              v-if="form.shipRoute"
              stroke-color="red"
              :path="masterRoute"
            />
            <div
              v-if="
                mapLoaded &&
                form.monitorType &&
                shipRouteResult.monitorRecords.length &&
                form.graphType === 'bar'
              "
            >
              <GmapMarker
                v-for="(record, idx) in getValidRecords(
                  shipRouteResult.monitorRecords,
                )"
                :key="`mtValue_${idx}`"
                :position="getRecordPos(record)"
                :icon="getRecordIcon(record)"
                :title="getRecordExplain(record)"
              />
            </div>
            <div
              v-if="
                mapLoaded &&
                form.monitorType &&
                shipRouteResult.monitorRecords.length &&
                form.graphType === 'heatmap'
              "
            >
              <gmap-heatmap-layer
                :data="heatmapMarker"
                :options="heatmapOption"
              />
            </div>
            <div v-if="form.ais">
              <div
                v-for="(ship, idx) in shipRouteResult.shipDataList"
                :key="`trace${idx}`"
              >
                <GmapPolyline stroke-color="blue" :path="ship.route" />
                <GmapMarker
                  v-for="(pos, markerIdx) in displaySelectedRoute(ship, idx)"
                  :key="`marker${markerIdx}`"
                  :position="pos"
                  :clickable="true"
                  :title="getShipTitle(ship, markerIdx)"
                  :icon="getShipIcon(markerIdx)"
                  @click="selectedMarker = idx"
                />
              </div>
            </div>
          </div>
        </GmapMap>
      </div>
    </b-card>
  </div>
</template>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<style scoped>
.legend {
  /* min-width: 100px;*/
  background-color: sliver;
}
</style>
<script lang="ts">
import Vue from 'vue';
import vSelect from 'vue-select';
import DatePicker from 'vue2-datepicker';
import 'vue2-datepicker/index.css';
import 'vue2-datepicker/locale/zh-tw';
const Ripple = require('vue-ripple-directive');
import { mapActions, mapGetters, mapMutations } from 'vuex';
import { Monitor } from '../store/monitors/types';
import moment from 'moment';
import axios from 'axios';
import { faShip, faFerry, faCircle } from '@fortawesome/free-solid-svg-icons';
import { MonitorType, RecordList, Position } from './types';

interface ShipData {
  name: string;
  route: Array<Position>;
}

interface ShipRouteResult {
  monitorRecords: Array<RecordList>;
  shipDataList: Array<ShipData>;
}

export default Vue.extend({
  components: {
    DatePicker,
    vSelect,
  },
  directives: {
    Ripple,
  },
  data() {
    const range = [moment().subtract(1, 'days').valueOf(), moment().valueOf()];
    let mapLoaded = false;
    let shipRouteResult: ShipRouteResult = {
      monitorRecords: new Array<RecordList>(),
      shipDataList: new Array<ShipData>(),
    };
    let mapOption = {
      zoomControl: true,
      mapTypeControl: true,
      scaleControl: true,
      streetViewControl: false,
      rotateControl: true,
      fullscreenControl: true,
    };
    let dataTypes = [
      { txt: '小時資料', id: 'hour' },
      { txt: '分鐘資料', id: 'min' },
    ];
    let graphOptions = [
      { text: '濃度圖', value: 'bar' },
      { text: '熱視圖', value: 'heatmap' },
    ];
    let heatmapOption = { dissipating: false, radius: 100, maxIntensity: 100 };
    return {
      statusFilters: [
        { id: 'all', txt: '全部' },
        { id: 'normal', txt: '正常量測值' },
        { id: 'calbration', txt: '校正' },
        { id: 'maintance', txt: '維修' },
        { id: 'invalid', txt: '無效數據' },
        { id: 'valid', txt: '有效數據' },
      ],
      form: {
        monitor: '',
        monitorType: '',
        dataType: 'hour',
        statusFilter: 'all',
        range,
        graphType: 'bar',
        shipRoute: false,
        ais: false,
      },
      dataTypes,
      graphOptions,
      shipRouteResult,
      mapOption,
      mapLoaded,
      heatmapOption,
      selectedMarker: -1,
    };
  },
  computed: {
    ...mapGetters('monitors', ['mMap', 'monitorOfNoEPA']),
    ...mapGetters('monitorTypes', ['mtMap', 'activatedMonitorTypes']),
    shipRouteTitle(): string {
      if (!this.form.monitor) return '';

      let mCase = this.mMap.get(this.form.monitor) as Monitor;
      let start = moment(this.form.range[0]).format('lll');
      let end = moment(this.form.range[1]).format('lll');
      return `${mCase.desc}${start}至${end}`;
    },
    displayRoute(): boolean {
      return this.mapLoaded && this.shipRouteResult.monitorRecords.length !== 0;
    },
    mapCenter(): Position {
      if (this.shipRouteResult.monitorRecords.length === 0)
        return { lat: 0, lng: 0 };

      let monitorRecords = this.shipRouteResult.monitorRecords;
      const lat = monitorRecords[0].mtDataList.find(
        mtRecord => mtRecord.mtName === 'LAT',
      )?.value;
      const lng = monitorRecords[0].mtDataList.find(
        mtRecord => mtRecord.mtName === 'LNG',
      )?.value;
      return { lat: lat ?? 0, lng: lng ?? 0 };
    },
    masterShipIcon(): any {
      if (!this.mapLoaded) return {};

      return {
        path: faFerry.icon[4] as string,
        fillColor: '#ff0000',
        fillOpacity: 1,
        anchor: new google.maps.Point(
          faShip.icon[0] / 2, // width
          faShip.icon[1], // height
        ),
        strokeWeight: 1,
        strokeColor: '#ffffff',
        scale: 0.06,
      };
    },
    masterRoute(): Array<Position> {
      if (!this.mapLoaded) return [];
      let ret = new Array<Position>();
      return this.shipRouteResult.monitorRecords.flatMap(recordList => {
        let latRecord = recordList.mtDataList.find(
          mtData => mtData.mtName === 'LAT',
        );
        let lngRecord = recordList.mtDataList.find(
          mtData => mtData.mtName === 'LNG',
        );
        if (
          latRecord === undefined ||
          latRecord.value === undefined ||
          lngRecord === undefined ||
          lngRecord.value === undefined
        )
          return [];
        else {
          return [
            {
              lat: latRecord.value,
              lng: lngRecord.value,
            },
          ];
        }
      });
    },
    heatmapMarker(): Array<any> {
      if (this.mapLoaded && this.shipRouteResult.monitorRecords.length !== 0) {
        let ret = new Array<any>();
        for (let recordList of this.shipRouteResult.monitorRecords) {
          let lat = this.getRecordValue(recordList, 'LAT');
          let lng = this.getRecordValue(recordList, 'LNG');
          let speed = this.getRecordValue(recordList, 'SPEED');
          let value = this.getLevelIndex(recordList);
          if (
            lat !== undefined &&
            lng !== undefined &&
            speed !== undefined &&
            value !== undefined
          ) {
            if (speed > 0.5)
              ret.push({
                location: new google.maps.LatLng({ lat, lng }),
                weight: value * speed,
              });
          }
        }
        return ret;
      }
      return [];
    },
  },
  async mounted() {
    await this.fetchMonitors();
    await this.fetchMonitorTypes();

    if (this.monitorOfNoEPA.length !== 0)
      this.form.monitor = this.monitorOfNoEPA[0]._id;

    this.$gmapApiPromiseLazy().then(() => {
      this.mapLoaded = true;
      const mapLegend = document.getElementById('mapLegend');
      let ref = this.$refs.mapRef as any;
      if (mapLegend !== null) {
        ref.$mapPromise.then((map: google.maps.Map) => {
          map.controls[google.maps.ControlPosition.BOTTOM_LEFT].push(mapLegend);
        });
      }
    });

    if (this.activatedMonitorTypes.length !== 0)
      this.form.monitorType = this.activatedMonitorTypes[0]._id;
  },
  methods: {
    ...mapActions('monitors', ['fetchMonitors']),
    ...mapActions('monitorTypes', ['fetchMonitorTypes']),
    ...mapMutations(['setLoading']),
    async query() {
      const url = `/ShipRoute/${this.form.monitor}/${this.form.dataType}/${this.form.statusFilter}/${this.form.ais}/${this.form.range[0]}/${this.form.range[1]}`;
      let mtCase = this.mtMap.get(this.form.monitorType) as MonitorType;
      let levels = mtCase.levels ?? [1, 2, 3, 4, 5];
      this.heatmapOption.maxIntensity = levels[levels.length - 1] * 10;
      this.heatmapOption.radius = this.form.dataType === 'hour' ? 100 : 50;
      this.selectedMarker = -1;
      try {
        this.setLoading({ loading: true });
        let res = await axios.get(url);
        if (res.status === 200) {
          let shipRouteResult = res.data as ShipRouteResult;
          this.shipRouteResult.monitorRecords = shipRouteResult.monitorRecords;
          this.shipRouteResult.shipDataList = shipRouteResult.shipDataList;
        }
      } catch (err) {
        console.error(`${err}`);
      } finally {
        this.setLoading({ loading: false });
      }
    },
    getRecordValue(recordList: RecordList, mt: string): number | undefined {
      let mtRecord = recordList.mtDataList.find(
        mtRecord => mtRecord.mtName === mt,
      );
      return mtRecord?.value;
    },
    getLevelIndex(recordList: RecordList): number {
      let mtCase = this.mtMap.get(this.form.monitorType) as MonitorType;
      let value = this.getRecordValue(recordList, this.form.monitorType);
      const levels = mtCase.levels ?? [1, 2, 3, 4, 5, 6];
      let v = value ?? 0;
      for (let i = 0; i < levels.length; i++) if (v < levels[i]) return i;

      return levels.length;
    },
    getRecordColor(recordList: RecordList): string {
      const colors = ['green', 'yellow', 'orange', 'red', 'purple', 'maroon'];
      let colorIdx = this.getLevelIndex(recordList);

      return colors[Math.min(colors.length - 1, colorIdx)];
    },
    getRecordIcon(recordList: RecordList): object {
      if (!this.mapLoaded) return {};

      return {
        path: faCircle.icon[4] as string,
        fillColor: this.getRecordColor(recordList),
        fillOpacity: 0.5,
        anchor: new google.maps.Point(
          faCircle.icon[0] / 2, // width
          faCircle.icon[1] / 2, // height
        ),
        strokeWeight: 0,
        strokeColor: '#000000',
        scale: this.form.dataType === 'hour' ? 0.06 : 0.04,
      };
    },
    getRecordPos(recordList: RecordList): any {
      let latRecord = recordList.mtDataList.find(
        mtData => mtData.mtName === 'LAT',
      );
      let lngRecord = recordList.mtDataList.find(
        mtData => mtData.mtName === 'LNG',
      );

      if (
        latRecord === undefined ||
        latRecord.value === undefined ||
        lngRecord === undefined ||
        lngRecord.value === undefined
      )
        return {};

      return {
        lat: latRecord.value,
        lng: lngRecord.value,
      };
    },
    getRecordExplain(record: RecordList): string {
      let mtCase = this.mtMap.get(this.form.monitorType) as MonitorType;
      let v = this.getRecordValue(record, this.form.monitorType);
      let vStr = v?.toFixed(mtCase.prec);
      let dtStr = moment(record._id.time).format('lll');
      return `${dtStr} ${vStr}${mtCase.unit}`;
    },
    getRecordLine(recordList: RecordList): Array<Position> {
      let lat = this.getRecordValue(recordList, 'LAT');
      let lng = this.getRecordValue(recordList, 'LNG');
      if (lat === undefined || lng === undefined) return [];

      const latDiffs = [0, 0.001, 0.0015, 0.00225, 0.003375];

      let diffIdx = this.getLevelIndex(recordList);
      let latDiff = latDiffs[Math.min(latDiffs.length - 1, diffIdx)];
      return [
        { lat, lng },
        { lat: lat + latDiff, lng },
      ];
    },
    getRecordWeight(recordList: RecordList): number {
      if (this.form.dataType === 'hour') return 3;

      return 1;
    },
    getRecordOption(recordList: RecordList): object {
      return {
        strokeWeight: this.getLevelIndex(recordList),
      };
    },
    getLevelExplain(idx: number): string {
      if (!this.form.monitorType) return '';

      let mtCase = this.mtMap.get(this.form.monitorType) as MonitorType;
      const levels = mtCase.levels ?? [1, 2, 3, 4, 5];
      if (idx == 0)
        return `${mtCase.desp}濃度 < ${levels[idx].toFixed(mtCase.prec)}${
          mtCase.unit
        }`;

      if (idx >= 1 && idx <= levels.length - 1)
        return `${mtCase.desp}濃度${levels[idx - 1].toFixed(
          mtCase.prec,
        )} < ${levels[idx].toFixed(mtCase.prec)}${mtCase.unit}`;

      return `${mtCase.desp}濃度 > ${levels[levels.length - 1].toFixed(
        mtCase.prec,
      )}${mtCase.unit}`;
    },
    getShipTitle(ship: ShipData, idx: number): string {
      let pos = ship.route[idx];
      let dt = pos.date;
      let speed = pos.speed ?? 0;
      if (dt !== undefined)
        return `${ship.name}: ${speed}節 ${moment(dt).format('lll')}`;

      return `${ship.name}: ${speed}節`;
    },
    getValidRecords(monitorRecords: Array<RecordList>): Array<RecordList> {
      return monitorRecords.filter(recordList => {
        let lat = this.getRecordValue(recordList, 'LAT');
        let lng = this.getRecordValue(recordList, 'LNG');
        let v = this.getRecordValue(recordList, this.form.monitorType);
        return lat !== undefined && lng !== undefined && v !== undefined;
      });
    },
    getShipIcon(idx: number): any {
      if (!this.mapLoaded) return {};

      if (idx === 0)
        return {
          path: faShip.icon[4] as string,
          fillColor: '#0000ff',
          fillOpacity: 1,
          anchor: new google.maps.Point(
            faShip.icon[0] / 2, // width
            faShip.icon[1], // height
          ),
          strokeWeight: 1,
          strokeColor: '#ffffff',
          scale: 0.04,
        };
      else
        return {
          path: faCircle.icon[4] as string,
          fillColor: '#ffffff',
          fillOpacity: 1,
          anchor: new google.maps.Point(
            faCircle.icon[0] / 2, // width
            faCircle.icon[1] / 2, // height
          ),
          strokeWeight: 1,
          strokeColor: '#ffffff',
          scale: 0.01,
        };
    },
    displaySelectedRoute(ship: ShipData, idx: number): Array<Position> {
      if (idx === this.selectedMarker) {
        return ship.route;
      } else {
        let posArray = new Array<Position>();
        posArray.push(ship.route[0]);
        return posArray;
      }
    },
  },
});
</script>

<template>
  <b-row class="match-height">
    <b-col
      v-for="mt in userInfo.monitorTypeOfInterest"
      :key="mt"
      cols="12"
      md="6"
      lg="4"
      xl="3"
    >
      <b-card border-variant="primary">
        <div :id="`history_${mt}`"></div>
      </b-card>
    </b-col>
    <b-col cols="12">
      <b-card
        v-for="aisData in latestAisData.aisData"
        :key="aisData.monitor"
        border-variant="primary"
        :title="getAisTitle(aisData)"
      >
        <div class="map_container">
          <GmapMap
            ref="map"
            :center="getMapCenter(aisData.monitor, aisData)"
            :zoom="13"
            map-type-id="hybrid"
            class="map_canvas"
            :options="mapOption"
          >
            <div v-if="mapLoaded">
              <GmapMarker
                v-if="mapLoaded"
                key="master"
                :position="getMasterPosition(aisData)"
                :clickable="true"
                :icon="getMasterShipIcon()"
                @click="toggleInfoWindow(aisData.monitor, 0)"
              />
              <GmapMarker
                v-for="(ship, idx) in shipWithPosition(aisData)"
                :key="idx + 1"
                :position="ship.position"
                :clickable="true"
                :title="ship.MMSI"
                :icon="getShipIcon(ship)"
                @click="toggleInfoWindow(aisData.monitor, idx + 1)"
              />
              <gmap-info-window
                :options="getInfoWindowOption(aisData)"
                :position="getInfoWindowPos(aisData)"
                :opened="getInfoWindowOpened(aisData)"
                @closeclick="setInfoWindowClosed(aisData)"
              />
            </div>
          </GmapMap>
        </div>
      </b-card>
    </b-col>
  </b-row>
</template>
<script lang="ts">
import Vue from 'vue';
import { mapActions, mapGetters, mapState } from 'vuex';
import axios from 'axios';
import { MonitorType, MtRecord, RecordList, Position, Group } from './types';
import highcharts from 'highcharts';
import darkTheme from 'highcharts/themes/dark-unica';
import useAppConfig from '../@core/app-config/useAppConfig';
import moment from 'moment';
import { Monitor } from '../store/monitors/types';
import { faShip, faFerry } from '@fortawesome/free-solid-svg-icons';

interface AisShip {
  MMSI: string;
  LAT: string;
  LON: string;
  SPEED: string;
  HEADING: string;
  COURSE: string;
  STATUS: string;
  TIMESTAMP: string;
  position?: Position;
}

interface ParsedAisData {
  monitor: string;
  time: number;
  ships: Array<AisShip>;
  lat?: number;
  lng?: number;
}

interface LatestAisData {
  enable: boolean;
  aisData: Array<ParsedAisData>;
}

interface DisplayRecordList extends RecordList {
  recordMap?: Map<string, MtRecord>;
}

interface LatestMonitorData {
  monitorTypes: Array<string>;
  monitorData: Array<RecordList>;
}

export default Vue.extend({
  data() {
    let latestAisData: LatestAisData = {
      enable: false,
      aisData: Array<ParsedAisData>(),
    };

    let infoWindoContent = new Map<string, string>();
    let infoWindowPos = new Map<string, any>();
    let infoWinOpen = new Map<string, boolean>();
    let infoWinIndex = new Map<string, number>();
    let mapLoaded = false;
    let mapOption = {
      zoomControl: true,
      mapTypeControl: true,
      scaleControl: true,
      streetViewControl: true,
      rotateControl: true,
      fullscreenControl: true,
    };
    let recordLists = Array<DisplayRecordList>();
    return {
      refreshTimer: 0,
      mtInterestTimer: 0,
      latestAisData,
      infoWindoContent,
      infoWindowPos,
      infoWinOpen,
      mapLoaded,
      infoWinIndex,
      mapOption,
      recordLists,
    };
  },
  computed: {
    ...mapState('user', ['userInfo', 'group']),
    ...mapState('monitors', ['monitors']),
    ...mapState('monitorTypes', ['monitorTypes']),
    ...mapGetters('monitorTypes', ['mtMap']),
    ...mapGetters('monitors', ['mMap', 'monitorOfNoEPA']),
    skin(): any {
      const { skin } = useAppConfig();
      return skin;
    },
  },
  async mounted() {
    const { skin } = useAppConfig();
    if (skin.value == 'dark') {
      darkTheme(highcharts);
    }

    this.$gmapApiPromiseLazy().then(() => {
      this.mapLoaded = true;
    });

    await this.fetchMonitorTypes();
    await this.fetchMonitors();
    await this.getUserInfo();
    const me = this;
    for (const mt of this.userInfo.monitorTypeOfInterest) this.query(mt);

    await this.getMonitorRealtimeData();
    await this.getLatestAisData();

    this.mtInterestTimer = setInterval(() => {
      me.getLatestAisData();
      me.getMonitorRealtimeData();
      for (const mt of me.userInfo.monitorTypeOfInterest) me.query(mt);
    }, 60000);
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer);
    clearInterval(this.mtInterestTimer);
  },
  methods: {
    ...mapActions('monitorTypes', ['fetchMonitorTypes']),
    ...mapActions('monitors', ['fetchMonitors']),
    ...mapActions('user', ['getUserInfo']),
    async query(mt: string) {
      let now = new Date().getTime();
      if (this.group) {
        let group = this.group as Group;
        if (group.delayHour) {
          now = now - group.delayHour * 60 * 60 * 1000;
        }
      }

      const oneHourBefore = now - 60 * 60 * 1000;
      const monitors = this.monitorOfNoEPA.map((m: Monitor) => m._id).join(':');
      const url = `/HistoryTrend/${monitors}/${mt}/Min/normal/${oneHourBefore}/${now}`;
      const res = await axios.get(url);
      const ret: highcharts.Options = res.data;

      ret.chart = {
        type: 'spline',
        zoomType: 'x',
        panning: {
          enabled: true,
        },
        panKey: 'shift',
        alignTicks: false,
      };

      let mtInfo = this.mtMap.get(mt) as MonitorType;
      ret.title!.text = `${mtInfo.desp}趨勢圖`;

      /*
      ret.colors = [
        '#7CB5EC',
        '#434348',
        '#90ED7D',
        '#F7A35C',
        '#8085E9',
        '#F15C80',
        '#E4D354',
        '#2B908F',
        '#FB9FA8',
        '#91E8E1',
        '#7CB5EC',
        '#80C535',
        '#969696',
      ];*/

      ret.tooltip = { valueDecimals: 2 };
      ret.legend = { enabled: true };
      ret.credits = {
        enabled: false,
        href: 'http://www.wecc.com.tw/',
      };

      ret.exporting = {
        enabled: false,
      };
      let xAxis: highcharts.XAxisOptions = ret.xAxis as highcharts.XAxisOptions;
      xAxis.type = 'datetime';

      xAxis!.dateTimeLabelFormats = {
        day: '%b%e日',
        week: '%b%e日',
        month: '%y年%b',
      };

      ret.plotOptions = {
        spline: {
          tooltip: {
            valueDecimals: this.mtMap.get(mt).prec,
          },
        },
        scatter: {
          tooltip: {
            valueDecimals: this.mtMap.get(mt).prec,
          },
        },
      };
      ret.time = {
        timezoneOffset: -480,
      };
      ret.exporting = {
        enabled: false,
      };
      highcharts.chart(`history_${mt}`, ret);
    },
    getMapCenter(monitor: string, data: ParsedAisData): any {
      // 船的位置
      if (data.lat !== undefined && data.lng !== undefined)
        return { lat: data.lat, lng: data.lng };

      // 船隻中心
      if (data.ships.length !== 0) {
        let lats = data.ships.map(ship => ship.position!.lat);
        let lngs = data.ships.map(ship => ship.position!.lng);

        let latMin = Math.min.apply(Math, lats);
        let latMax = Math.max.apply(Math, lats);
        let lngMin = Math.min.apply(Math, lngs);
        let lngMax = Math.max.apply(Math, lngs);
        let lat = (latMin + latMax) / 2;
        let lng = (lngMin + lngMax) / 2;
        return { lat, lng };
      }

      // 港口中心
      let mCase = this.mMap.get(monitor) as Monitor;
      return {
        lat: mCase.lat,
        lng: mCase.lng,
      };
    },
    getMtName(mt: string): string {
      let mtInfo = this.mtMap.get(mt) as MonitorType;
      return mtInfo?.desp ?? '';
    },
    async getLatestAisData(): Promise<void> {
      try {
        let ret = await axios.get('/LatestAisData');
        if (ret.status === 200) {
          this.latestAisData = ret.data;
          for (let data of this.latestAisData.aisData) {
            for (let ship of data.ships) {
              let lat = Number.parseFloat(ship.LAT);
              let lng = Number.parseFloat(ship.LON);
              if (lat !== NaN && lng !== NaN) ship.position = { lat, lng };
            }
          }
        }
      } catch (err) {
        console.error(err);
      }
    },
    getAisTitle(data: ParsedAisData): string {
      return `${this.mMap.get(data.monitor).desc} AIS 即時圖`;
    },
    getMonitorName(m: string): string {
      return this.mMap.get(m).desc;
    },
    getMasterPosition(aisData: ParsedAisData): any {
      return {
        lat: aisData.lat,
        lng: aisData.lng,
      };
    },
    getShipIcon(ship: AisShip): any {
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
    },
    getMasterShipIcon(): any {
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
    toggleInfoWindow(monitor: string, idx: number) {
      let aisData = this.latestAisData.aisData.find(
        data => data.monitor === monitor,
      );

      if (aisData) {
        let masterPos = {
          lat: aisData.lat,
          lng: aisData.lng,
        };
        if (idx === 0) {
          this.infoWindowPos.set(monitor, masterPos);
          this.infoWindowPos = new Map(this.infoWindowPos);

          let distance = '';
          let monitorData = this.mMap.get(monitor);
          let recordList = this.recordLists.find(
            rec => rec._id.monitor === monitor,
          );
          {
            let latRecord = recordList?.recordMap!.get('LAT');
            let lngRecord = recordList?.recordMap!.get('LNG');
            if (
              monitorData === undefined ||
              monitorData.lat === undefined ||
              monitorData.lng === undefined ||
              latRecord === undefined ||
              latRecord.value === undefined ||
              lngRecord === undefined ||
              lngRecord.value === undefined
            ) {
              distance = '?';
            } else {
              let km = this.getDistanceFromLatLonInKm(
                monitorData.lat,
                monitorData.lng,
                latRecord?.value,
                lngRecord?.value,
              );
              distance = `${km?.toFixed(1)}`;
            }
          }

          let content =
            `<strong>${this.mMap.get(monitor).desc}</strong><br\>` +
            `離港距離:${distance} km`;
          if (recordList !== undefined)
            for (let mt of this.userInfo.monitorTypeOfInterest) {
              content += `<br/>${this.geMtRecordValue(recordList, mt)}`;
            }
          this.infoWindoContent = new Map(
            this.infoWindoContent.set(monitor, content),
          );
        } else {
          let ship = aisData.ships[idx - 1];
          if (ship.position) {
            this.infoWindowPos = new Map(
              this.infoWindowPos.set(monitor, ship!.position),
            );
          }

          function getValue(v: string | undefined) {
            if (v === '511' || v == '-1') return '未知';
            else return v;
          }

          let content =
            `<strong>${ship.MMSI}</strong>` +
            `<p>速度:${getValue(ship.SPEED)}<br/>
                方向:${getValue(ship.HEADING)}<br/>
                時間:${moment(ship.TIMESTAMP).add(8, 'hour').format('lll')}
            </p>`;
          this.infoWindoContent = new Map(
            this.infoWindoContent.set(monitor, content),
          );
        }
        if (this.infoWinIndex.get(monitor) === idx) {
          let current = this.infoWinOpen.get(monitor);
          this.infoWinOpen = new Map(this.infoWinOpen.set(monitor, !current));
        } else {
          this.infoWinIndex = new Map(this.infoWinIndex.set(monitor, idx));
          this.infoWinOpen = new Map(this.infoWinOpen.set(monitor, true));
        }

        if (!this.infoWinOpen.get(monitor)) {
          this.infoWinOpen = new Map(this.infoWinOpen.set(monitor, true));
        }
      } else console.error(monitor);
    },
    getInfoWindowOpened(aisData: ParsedAisData): boolean {
      if (this.infoWinOpen.get(aisData.monitor)) return true;

      return false;
    },
    setInfoWindowClosed(aisData: ParsedAisData): void {
      this.infoWinOpen = new Map(this.infoWinOpen.set(aisData.monitor, false));
    },
    getInfoWindowPos(aisData: ParsedAisData): any {
      return this.infoWindowPos.get(aisData.monitor);
    },
    getInfoWindowOption(aisData: ParsedAisData): any {
      let content = this.infoWindoContent.get(aisData.monitor);
      return {
        content,
        // optional: offset infowindow so it visually sits nicely on top of our marker
        pixelOffset: {
          width: 0,
          height: -35,
        },
      };
    },
    shipWithPosition(aisData: ParsedAisData): Array<AisShip> {
      return aisData.ships.filter(ship => ship.position);
    },
    async getMonitorRealtimeData() {
      const ret = await axios.get('/LatestMonitorData');
      let data = ret.data as LatestMonitorData;
      this.recordLists = data.monitorData;
      for (let recordList of this.recordLists) {
        recordList.recordMap = new Map<string, MtRecord>();
        for (let mtData of recordList.mtDataList) {
          recordList.recordMap.set(mtData.mtName, mtData);
        }
      }
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
    geMtRecordValue(recordList: DisplayRecordList, mt: string): string {
      let mtRecord = recordList?.recordMap!.get(mt);
      let mtCase = this.mtMap.get(mt) as MonitorType;
      if (mtRecord === undefined || mtRecord.value === undefined)
        return `${mtCase.desp}: - ${mtCase.unit}`;

      return `${mtCase.desp}: ${mtRecord.value.toFixed(mtCase.prec)} ${
        mtCase.unit
      }`;
    },
  },
});
</script>

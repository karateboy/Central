<template>
  <b-table :fields="fields" :items="chs">
    <template #cell(enable)="row">
      <b-form-checkbox v-model="row.item.enable" @change="onChange" />
    </template>
    <template #cell(repairMode)="row">
      <b-form-checkbox v-model="row.item.repairMode" @change="onChange" />
    </template>
    <template #cell(mt)="row">
      <v-select
        id="monitorType"
        v-model="row.item.mt"
        label="desp"
        :reduce="mt => mt._id"
        :options="monitorTypes"
        @input="onChange"
      />
    </template>
    <template #cell(max)="row">
      <b-form-input v-model.number="row.item.max" @change="onChange" />
    </template>
    <template #cell(min)="row">
      <b-form-input v-model.number="row.item.min" @change="onChange" />
    </template>
    <template #cell(mtMax)="row">
      <b-form-input v-model.number="row.item.mtMax" @change="onChange" />
    </template>
    <template #cell(mtMin)="row">
      <b-form-input v-model.number="row.item.mtMin" @change="onChange" />
    </template>
  </b-table>
</template>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<script lang="ts">
import Vue from 'vue';
import { mapState, mapGetters } from 'vuex';
import vSelect from 'vue-select';
import { isNumber } from 'highcharts';
interface AiChannelCfg {
  enable: boolean;
  mt?: string;
  max?: number;
  mtMax?: number;
  min?: number;
  mtMin?: number;
  repairMode?: boolean;
}

export default Vue.extend({
  components: {
    vSelect,
  },
  props: {
    paramStr: {
      type: String,
      default: ``,
    },
  },
  data() {
    let chs = Array<AiChannelCfg>();
    for (let i = 0; i < 8; i++) {
      chs.push({
        enable: false,
        mt: undefined,
        max: undefined,
        mtMax: undefined,
        min: undefined,
        mtMin: undefined,
        repairMode: false,
      });
    }

    if (this.paramStr !== '') chs = JSON.parse(this.paramStr);

    const fields = [
      {
        key: 'enable',
        label: '啟用',
      },
      {
        key: 'repairMode',
        label: '維修模式',
      },
      {
        key: 'mt',
        label: '測項',
      },
      {
        key: 'min',
        label: '電壓最小值',
      },
      {
        key: 'max',
        label: '電壓最大值',
      },
      {
        key: 'mtMin',
        label: '測項最小值',
      },
      {
        key: 'mtMax',
        label: '測項最大值',
      },
    ];

    return {
      chs,
      fields,
    };
  },
  computed: {
    ...mapState('monitorTypes', ['monitorTypes']),
    ...mapGetters('monitorTypes', ['mtMap']),
  },
  methods: {
    justify() {
      for (const ch of this.chs) {
        if (!isNumber(ch.min)) ch.min = undefined;
        if (!isNumber(ch.max)) ch.max = undefined;
        if (!isNumber(ch.mtMin)) ch.mtMin = undefined;
        if (!isNumber(ch.mtMax)) ch.mtMax = undefined;
      }
    },
    onChange(evt: any) {
      this.justify();
      this.$emit('param-changed', JSON.stringify(this.chs));
    },
  },
});
</script>

<template>
  <div>
    <b-form-group
      v-if="hasAddr"
      label="位址"
      label-for="address"
      label-cols-md="3"
    >
      <b-form-input
        v-model.number="paramObj.addr"
        @change="onChange"
      ></b-form-input>
    </b-form-group>
    <b-table :fields="fields" :items="paramObj.chs">
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
    <b-table :fields="doFields" :items="paramObj.doChannels">
      <template #cell(monitorType)="row">
        <v-select
          id="monitorType"
          v-model="row.item.monitorType"
          label="desp"
          :reduce="mt => mt._id"
          :options="signalTypes"
          @input="onChange"
        />
      </template>
    </b-table>
  </div>
</template>
<style lang="scss">
@import '@core/scss/vue/libs/vue-select.scss';
</style>
<script>
import Vue from 'vue';
import { mapState, mapGetters } from 'vuex';
import vSelect from 'vue-select';
import axios from 'axios';

export default Vue.extend({
  components: {
    vSelect,
  },
  props: {
    paramStr: {
      type: String,
      default: ``,
    },
    hasAddr: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    let chs = [];
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
    let doChannels = [];
    for (let i = 0; i < 2; i++) {
      doChannels.push({
        monitorType: undefined,
      });
    }
    let addr = 1;
    let paramObj = { addr, chs, doChannels };

    if (this.paramStr !== '{}') paramObj = JSON.parse(this.paramStr);

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

    const doFields = [
      {
        key: 'monitorType',
        label: '數位訊號',
      },
    ];

    let signalTypes = [];
    return {
      paramObj,
      doFields,
      fields,
      signalTypes,
    };
  },
  computed: {
    ...mapState('monitorTypes', ['monitorTypes']),
    ...mapGetters('monitorTypes', ['mtMap']),
  },
  async mounted() {
    await this.getSignalTypes();
  },
  methods: {
    justify() {
      for (const ch of this.paramObj.chs) {
        if (ch.min === '') ch.min = undefined;
        if (ch.max === '') ch.max = undefined;
        if (ch.mtMin === '') ch.mtMin = undefined;
        if (ch.mtMax === '') ch.mtMax = undefined;
      }
    },
    onChange(evt) {
      this.justify();
      this.$emit('param-changed', JSON.stringify(this.paramObj));
    },
    async getSignalTypes() {
      const res = await axios.get('/SignalTypes');
      this.signalTypes = res.data;
    },
  },
});
</script>

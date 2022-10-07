export interface Monitor {
  _id: string;
  desc: string;
  order: number;
  lat?: number;
  lng?: number;
  epaId?: number;
  monitorTypes: Array<string>;
}

export interface MonitorState {
  monitors: Array<Monitor>;
  activeID: string;
}

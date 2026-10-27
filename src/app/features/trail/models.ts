/** Műveleti trail: a backend TrailQuery válaszai. */
export interface TrailOperation {
  traceId: string;
  startedAt: string;
  operation: string;
  service: string;
  user: string | null;
  durationMs: number;
  completeMs: number | null;
  status: string;
  steps: number;
  services: string[];
  errors: number;
  byKind: Record<string, number>;
}

export interface TrailStep {
  spanId: string;
  parentId: string | null;
  service: string;
  kind: string;
  operation: string;
  target: string | null;
  startedAt: string;
  offsetMs: number;
  durationMs: number;
  queueMs: number | null;
  e2eMs: number | null;
  status: string;
  detail: string | null;
  root: boolean;
  user: string | null;
}

export interface TrailDetail {
  traceId: string;
  startedAt: string;
  durationMs: number;
  completeMs: number | null;
  steps: TrailStep[];
}

export interface TrailStat {
  bucket: string;
  service: string;
  kind: string;
  operation: string;
  count: number;
  avgMs: number;
  maxMs: number;
  avgQueueMs: number | null;
  avgE2eMs: number | null;
  errors: number;
}

export const KIND_LABEL: Record<string, string> = {
  GATEWAY: 'Gateway',
  HTTP: 'HTTP kérés',
  SQL: 'SQL (PostgreSQL)',
  KAFKA_PUBLISH: 'Kafka küldés',
  KAFKA_CONSUME: 'Kafka feldolgozás',
  MONGO: 'MongoDB írás',
  WEBHOOK: 'Webhook',
};

export const KIND_COLOR: Record<string, string> = {
  GATEWAY: '#118DFF',
  HTTP: '#50c4c4',
  SQL: '#FD8400',
  KAFKA_PUBLISH: '#8e6bd8',
  KAFKA_CONSUME: '#b04fc4',
  MONGO: '#51a351',
  WEBHOOK: '#ED2447',
};

export const KINDS = Object.keys(KIND_LABEL);

export type Allapot = 'ERKEZTETETT' | 'BONTOTT' | 'IKTATOTT';

export interface Kuldemeny {
  id: string;
  allapot: Allapot;
  erkezesDatum: string;
  targy: string | null;
  partnerId: string;
  partnerNev: string;
  partnerCim: string | null;
  erkeztetteNev: string | null;
  bontasDatum: string | null;
  mellekletekSzama: number | null;
  iktatoszam: string | null;
  iktatasIdeje: string | null;
  ugyintezoId: string | null;
  ugyintezoNev: string | null;
  version: number;
}

export const ALLAPOT_NEV: Record<Allapot, string> = {
  ERKEZTETETT: 'Érkeztetett',
  BONTOTT: 'Bontott',
  IKTATOTT: 'Iktatott',
};

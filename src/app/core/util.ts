/** Helyi dátum ISO (yyyy-MM-dd) formában, időzóna-eltolás nélkül. */
export function isoDate(d: Date | string | null | undefined): string | null {
  if (!d) {
    return null;
  }
  if (typeof d === 'string') {
    return d.substring(0, 10);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

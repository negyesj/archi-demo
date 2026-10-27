import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Magyar lapozó feliratok (DMS One státuszsor). */
@Injectable()
export class HuPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Sorok oldalanként:';
  override nextPageLabel = 'Következő oldal';
  override previousPageLabel = 'Előző oldal';
  override firstPageLabel = 'Első oldal';
  override lastPageLabel = 'Utolsó oldal';
  override getRangeLabel = (page: number, size: number, length: number): string => {
    if (length === 0 || size === 0) {
      return `0 / ${length}`;
    }
    const start = page * size;
    return `${start + 1}–${Math.min(start + size, length)} / ${length}`;
  };
}
